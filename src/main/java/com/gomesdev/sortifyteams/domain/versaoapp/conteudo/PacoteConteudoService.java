package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Publicação de pacotes de conteúdo (spec 002, Fase 5).
 *
 * <p>Entrada: o ZIP da pasta gerada por {@code npx expo export --platform
 * android}. O serviço lê o {@code metadata.json} do export para descobrir qual
 * arquivo é o bundle e quais são os assets, grava cada um endereçado por hash
 * e monta o pacote.
 *
 * <p>Assets já conhecidos (mesmo hash) são <b>reaproveitados</b> em vez de
 * duplicados — é o que faz uma entrega que só mexeu no JS não trazer as
 * imagens de novo (FR-004).
 */
@Service
public class PacoteConteudoService {

    private static final String METADATA = "metadata.json";
    private static final long LIMITE_ARQUIVO_BYTES = 64L * 1024 * 1024;

    private final AssetConteudoRepository assetRepository;
    private final PacoteConteudoRepository pacoteRepository;
    private final PacoteAssetRepository pacoteAssetRepository;
    private final AssetBinarioRepository binarioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PacoteConteudoService(AssetConteudoRepository assetRepository,
                                 PacoteConteudoRepository pacoteRepository,
                                 PacoteAssetRepository pacoteAssetRepository,
                                 AssetBinarioRepository binarioRepository) {
        this.assetRepository = assetRepository;
        this.pacoteRepository = pacoteRepository;
        this.pacoteAssetRepository = pacoteAssetRepository;
        this.binarioRepository = binarioRepository;
    }

    @Transactional(readOnly = true)
    public List<PacoteConteudo> listar() {
        return pacoteRepository.findAllByOrderByPublicadoEmDesc();
    }

    @Transactional(readOnly = true)
    public Optional<PacoteConteudo> ativoDoRuntime(String runtimeVersion) {
        if (runtimeVersion == null || runtimeVersion.isBlank()) {
            return Optional.empty();
        }
        return pacoteRepository.findByRuntimeVersionAndAtivoTrue(runtimeVersion.trim());
    }

    @Transactional(readOnly = true)
    public List<PacoteAsset> assetsDo(String pacoteId) {
        return pacoteAssetRepository.findByPacoteId(pacoteId);
    }

    @Transactional(readOnly = true)
    public AssetConteudo porHash(String hash) {
        return assetRepository.findByHash(hash)
                .orElseThrow(() -> new EntityNotFoundException("Asset não encontrado: " + hash));
    }

    @Transactional(readOnly = true)
    public AssetConteudo porId(String id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Asset não encontrado: " + id));
    }

    /**
     * Publica um pacote a partir do ZIP do export e o torna o ativo do runtime.
     */
    @Transactional
    public PacoteConteudo publicar(MultipartFile zipDoExport, String runtimeVersion, String notas,
                                   String publicadoPorId) {
        if (runtimeVersion == null || runtimeVersion.isBlank()) {
            throw new IllegalArgumentException("Informe o runtimeVersion do pacote.");
        }
        if (zipDoExport == null || zipDoExport.isEmpty()) {
            throw new IllegalArgumentException("Envie o ZIP do export (npx expo export).");
        }

        Map<String, byte[]> arquivos = descompactar(zipDoExport);
        byte[] metadata = arquivos.get(METADATA);
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "O ZIP não tem metadata.json na raiz — envie o conteúdo da pasta dist do export.");
        }

        ExportAndroid export = lerMetadata(metadata);
        byte[] bundle = exigir(arquivos, export.bundle());

        AssetConteudo launchAsset = guardar(bundle, "application/javascript");
        PacoteConteudo pacote = new PacoteConteudo(runtimeVersion.trim(), launchAsset.getId(),
                notas, publicadoPorId);
        pacoteRepository.desativarDoRuntime(runtimeVersion.trim());
        pacote.setAtivo(true);
        pacoteRepository.save(pacote);
        pacoteRepository.flush();

        pacoteAssetRepository.save(new PacoteAsset(pacote.getId(), launchAsset.getId(),
                export.bundle(), "hbc"));

        for (AssetDoExport asset : export.assets()) {
            byte[] conteudo = exigir(arquivos, asset.path());
            AssetConteudo guardado = guardar(conteudo, tipoPorExtensao(asset.ext()));
            pacoteAssetRepository.save(new PacoteAsset(pacote.getId(), guardado.getId(),
                    asset.path(), asset.ext()));
        }
        return pacote;
    }

    @Transactional
    public PacoteConteudo ativar(String id) {
        PacoteConteudo pacote = pacoteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pacote não encontrado: " + id));
        pacoteRepository.desativarDoRuntime(pacote.getRuntimeVersion());
        pacote.setAtivo(true);
        return pacoteRepository.save(pacote);
    }

    @Transactional
    public PacoteConteudo despublicar(String id) {
        PacoteConteudo pacote = pacoteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pacote não encontrado: " + id));
        pacote.setAtivo(false);
        return pacoteRepository.save(pacote);
    }

    // ---------- internos ----------

    /** Reaproveita o asset quando o hash já existe (dedup — FR-004). */
    private AssetConteudo guardar(byte[] conteudo, String contentType) {
        String hash = hashBase64Url(conteudo);
        return assetRepository.findByHash(hash).orElseGet(() -> {
            AssetConteudo asset = assetRepository.save(
                    new AssetConteudo(hash, contentType, conteudo.length));
            assetRepository.flush();
            binarioRepository.gravar(asset.getId(), conteudo);
            return asset;
        });
    }

    private Map<String, byte[]> descompactar(MultipartFile zip) {
        Map<String, byte[]> arquivos = new HashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = entrada.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String nome = normalizar(entry.getName());
                if (nome == null) {
                    continue;
                }
                ByteArrayOutputStream saida = new ByteArrayOutputStream();
                long total = 0;
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = entrada.read(buffer)) != -1) {
                    total += lidos;
                    if (total > LIMITE_ARQUIVO_BYTES) {
                        throw new IllegalArgumentException(
                                "Arquivo %s passa do limite aceito no pacote.".formatted(nome));
                    }
                    saida.write(buffer, 0, lidos);
                }
                arquivos.put(nome, saida.toByteArray());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o ZIP do export.", e);
        }
        return arquivos;
    }

    /**
     * Normaliza o caminho da entrada do ZIP e barra travessia de diretório
     * ({@code ../}) — o ZIP é enviado por um admin, mas continua sendo entrada
     * externa sendo desempacotada.
     */
    private String normalizar(String nome) {
        String limpo = nome.replace('\\', '/');
        if (limpo.contains("../") || limpo.startsWith("/")) {
            return null;
        }
        // Tolera o ZIP ter sido criado com a pasta dist/ na raiz.
        if (limpo.startsWith("dist/")) {
            limpo = limpo.substring("dist/".length());
        }
        return limpo.isBlank() ? null : limpo;
    }

    private byte[] exigir(Map<String, byte[]> arquivos, String caminho) {
        byte[] conteudo = arquivos.get(caminho);
        if (conteudo == null) {
            throw new IllegalArgumentException(
                    "O ZIP não contém o arquivo %s citado no metadata.json.".formatted(caminho));
        }
        return conteudo;
    }

    private ExportAndroid lerMetadata(byte[] metadata) {
        try {
            JsonNode raiz = objectMapper.readTree(metadata);
            JsonNode android = raiz.path("fileMetadata").path("android");
            if (android.isMissingNode() || android.isNull()) {
                throw new IllegalArgumentException(
                        "O export não tem a plataforma android — rode com --platform android.");
            }
            String bundle = android.path("bundle").asText(null);
            if (bundle == null) {
                throw new IllegalArgumentException("O metadata.json não aponta o bundle do android.");
            }
            List<AssetDoExport> assets = new java.util.ArrayList<>();
            for (JsonNode asset : android.path("assets")) {
                assets.add(new AssetDoExport(asset.path("path").asText(), asset.path("ext").asText("")));
            }
            return new ExportAndroid(bundle, assets);
        } catch (IOException e) {
            throw new IllegalArgumentException("metadata.json inválido no ZIP enviado.");
        }
    }

    /** O protocolo de updates espera SHA-256 em base64 url-safe, sem padding. */
    static String hashBase64Url(byte[] conteudo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(conteudo);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    static String tipoPorExtensao(String ext) {
        if (ext == null) {
            return "application/octet-stream";
        }
        return switch (ext.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "json" -> "application/json";
            case "js", "hbc", "bundle" -> "application/javascript";
            default -> "application/octet-stream";
        };
    }

    private record ExportAndroid(String bundle, List<AssetDoExport> assets) {
    }

    private record AssetDoExport(String path, String ext) {
    }
}
