package com.gomesdev.sortifyteams.domain.versaoapp;

import com.gomesdev.sortifyteams.domain.versaoapp.request.PublicarVersaoRequest;
import com.gomesdev.sortifyteams.enums.PlataformaAppEnum;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Publicação de versões de runtime do app (spec 002, FR-017/FR-018).
 *
 * <p>A publicação é atômica por construção: metadados e binário são gravados
 * na mesma transação (C22). Falhando no meio, nada sobra — nem versão sem
 * arquivo, nem arquivo órfão.
 *
 * <p><b>Metadados vêm do próprio APK</b>, não de digitação no formulário
 * (revisão do FR-017): {@code versionCode}, {@code versionName} e o
 * {@code runtimeVersion} do Expo já estão gravados no
 * {@code AndroidManifest.xml} — {@link ApkManifestReader} lê de lá. Isso
 * exige acesso aleatório ao zip (não dá para ler de um {@link InputStream}
 * de uma passada só), então o upload é primeiro copiado para um arquivo
 * temporário, apagado no {@code finally}.
 */
@Service
public class VersaoAppService {

    /** Assinatura de arquivo ZIP — todo APK é um zip (checagem de FR-017). */
    private static final byte[] MAGIC_ZIP = {0x50, 0x4B, 0x03, 0x04};

    private final VersaoRuntimeRepository versaoRepository;
    private final VersaoRuntimeArquivoRepository arquivoRepository;
    private final ApkBinarioRepository binarioRepository;
    private final ApkManifestReader manifestReader;

    public VersaoAppService(VersaoRuntimeRepository versaoRepository,
                            VersaoRuntimeArquivoRepository arquivoRepository,
                            ApkBinarioRepository binarioRepository,
                            ApkManifestReader manifestReader) {
        this.versaoRepository = versaoRepository;
        this.arquivoRepository = arquivoRepository;
        this.binarioRepository = binarioRepository;
        this.manifestReader = manifestReader;
    }

    @Transactional(readOnly = true)
    public List<VersaoRuntime> listar(PlataformaAppEnum plataforma) {
        return versaoRepository.findByPlataformaOrderByVersionCodeDesc(plataforma);
    }

    @Transactional(readOnly = true)
    public Optional<VersaoRuntime> ativa(PlataformaAppEnum plataforma) {
        return versaoRepository.findByPlataformaAndAtivaTrue(plataforma);
    }

    @Transactional(readOnly = true)
    public Optional<VersaoRuntime> porVersionCode(PlataformaAppEnum plataforma, int versionCode) {
        return versaoRepository.findByPlataformaAndVersionCode(plataforma, versionCode);
    }

    @Transactional(readOnly = true)
    public VersaoRuntime buscar(String id) {
        return versaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Versão não encontrada: " + id));
    }

    /**
     * Publica uma versão lendo versão/versionCode/runtimeVersion do próprio
     * APK. Caminho normal — usado pelo painel (T003).
     */
    @Transactional
    public VersaoRuntime publicar(PublicarVersaoRequest request, MultipartFile arquivo,
                                  PlataformaAppEnum plataforma, String publicadaPorId) {
        return publicar(request, FonteApk.de(arquivo), plataforma, publicadaPorId);
    }

    @Transactional
    public VersaoRuntime publicar(PublicarVersaoRequest request, FonteApk arquivo,
                                  PlataformaAppEnum plataforma, String publicadaPorId) {
        Path temp = copiarParaTemp(arquivo);
        try {
            validarZip(temp);
            ApkManifest manifest = manifestReader.ler(temp);
            return publicarValidado(temp, manifest.versionCode(), manifest.versionName(),
                    manifest.runtimeVersion(), request.versionCodeMinimo(), request.notas(),
                    plataforma, publicadaPorId);
        } finally {
            apagarTemp(temp);
        }
    }

    /**
     * Publica com metadados informados explicitamente, sem ler o manifesto.
     *
     * <p><b>Uso restrito ao bootstrap (C15/T006).</b> O APK 1.0.0 distribuído
     * antes desta feature não tem o plugin {@code expo-updates} configurado —
     * não existe {@code runtimeVersion} para extrair dele, porque o conceito
     * não existia quando ele foi buildado. Qualquer publicação nova (painel)
     * usa {@link #publicar(PublicarVersaoRequest, FonteApk, PlataformaAppEnum, String)}.
     */
    @Transactional
    public VersaoRuntime publicarComMetadadosExplicitos(FonteApk arquivo, String versao, int versionCode,
                                                        String runtimeVersion, int versionCodeMinimo,
                                                        String notas, PlataformaAppEnum plataforma,
                                                        String publicadaPorId) {
        Path temp = copiarParaTemp(arquivo);
        try {
            validarZip(temp);
            return publicarValidado(temp, versionCode, versao, runtimeVersion, versionCodeMinimo, notas,
                    plataforma, publicadaPorId);
        } finally {
            apagarTemp(temp);
        }
    }

    private VersaoRuntime publicarValidado(Path temp, int versionCode, String versao, String runtimeVersion,
                                           int versionCodeMinimo, String notas,
                                           PlataformaAppEnum plataforma, String publicadaPorId) {
        validarMetadados(versionCode, versionCodeMinimo, plataforma);
        Hashes hashes = calcularHashes(temp);

        VersaoRuntime versaoRuntime = new VersaoRuntime(plataforma, versao, versionCode,
                runtimeVersion, notas, versionCodeMinimo, publicadaPorId);
        versaoRuntime.setTamanhoBytes(hashes.tamanho());
        versaoRuntime.setSha256(hashes.sha256());
        versaoRuntime.setMd5(hashes.md5());

        versaoRepository.desativarTodas(plataforma);
        versaoRuntime.setAtiva(true);
        versaoRepository.save(versaoRuntime);
        versaoRepository.flush();

        arquivoRepository.save(new VersaoRuntimeArquivo(versaoRuntime.getId()));
        arquivoRepository.flush();
        binarioRepository.gravar(versaoRuntime.getId(), abrirTemp(temp), hashes.tamanho());

        return versaoRuntime;
    }

    /**
     * Volta para uma versão anterior (C14). Só é permitido se o binário ainda
     * existir — depois do expurgo da C23 a versão é apenas histórico (FR-031).
     */
    @Transactional
    public VersaoRuntime ativar(String id) {
        VersaoRuntime versao = buscar(id);
        if (!binarioRepository.existeBinario(versao.getId())) {
            throw new IllegalArgumentException(
                    "A versão %s não tem mais o binário (expurgada em %s) — não é possível ativá-la."
                            .formatted(versao.getVersao(), versao.getBinarioExpurgadoEm()));
        }
        versaoRepository.desativarTodas(versao.getPlataforma());
        versao.setAtiva(true);
        return versaoRepository.save(versao);
    }

    /**
     * Tira a versão do ar sem apagá-la. Some a resposta da checagem até que
     * outra seja ativada — usado quando uma publicação se mostra ruim.
     */
    @Transactional
    public VersaoRuntime despublicar(String id) {
        VersaoRuntime versao = buscar(id);
        versao.setAtiva(false);
        return versaoRepository.save(versao);
    }

    // ---------- validações (FR-017) ----------

    private void validarZip(Path temp) {
        byte[] cabecalho = new byte[MAGIC_ZIP.length];
        try (InputStream in = Files.newInputStream(temp)) {
            int lidos = in.readNBytes(cabecalho, 0, cabecalho.length);
            if (lidos < MAGIC_ZIP.length || !java.util.Arrays.equals(cabecalho, MAGIC_ZIP)) {
                throw new IllegalArgumentException(
                        "O arquivo não parece um APK válido (assinatura de conteúdo inesperada).");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private void validarMetadados(int versionCode, int versionCodeMinimo, PlataformaAppEnum plataforma) {
        if (versionCodeMinimo > versionCode) {
            throw new IllegalArgumentException(
                    "O versionCode mínimo suportado (%d) não pode ser maior que o do próprio APK (%d)."
                            .formatted(versionCodeMinimo, versionCode));
        }
        versaoRepository.maiorVersionCode(plataforma).ifPresent(maior -> {
            if (versionCode <= maior) {
                throw new IllegalArgumentException(
                        ("O versionCode deste APK (%d) é menor ou igual ao já publicado (%d). O Android recusa "
                                + "instalar por cima de uma versão igual ou mais nova — gere um novo build "
                                + "com versionCode maior.")
                                .formatted(versionCode, maior));
            }
        });
    }

    // ---------- arquivo temporário e hashes (D4) ----------

    private Path copiarParaTemp(FonteApk arquivo) {
        if (arquivo == null || arquivo.vazio()) {
            throw new IllegalArgumentException("Envie o arquivo APK.");
        }
        String nome = arquivo.nome();
        if (nome == null || !nome.toLowerCase().endsWith(".apk")) {
            throw new IllegalArgumentException("O arquivo precisa ser um APK (.apk).");
        }
        try {
            Path temp = Files.createTempFile("versao-app-", ".apk");
            try (InputStream in = arquivo.abrir()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private void apagarTemp(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // arquivo temporario do SO — nao impede a publicacao ja concluida
        }
    }

    private Hashes calcularHashes(Path temp) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            long tamanho = 0;
            try (InputStream base = Files.newInputStream(temp);
                 DigestInputStream comSha = new DigestInputStream(base, sha256);
                 DigestInputStream in = new DigestInputStream(comSha, md5)) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = in.read(buffer)) != -1) {
                    tamanho += lidos;
                }
            }
            return new Hashes(HexFormat.of().formatHex(sha256.digest()),
                    HexFormat.of().formatHex(md5.digest()), tamanho);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash indisponível na JVM.", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private InputStream abrirTemp(Path temp) {
        try {
            return Files.newInputStream(temp);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private record Hashes(String sha256, String md5, long tamanho) {
    }

    /** Marca a versão como expurgada — usado pelo agendado da C23 (Fase 7). */
    @Transactional
    public void marcarExpurgada(VersaoRuntime versao) {
        versao.setBinarioExpurgadoEm(LocalDateTime.now());
        versaoRepository.save(versao);
    }
}
