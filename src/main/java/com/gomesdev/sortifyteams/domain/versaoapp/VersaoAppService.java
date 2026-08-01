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
 */
@Service
public class VersaoAppService {

    /** Assinatura de arquivo ZIP — todo APK é um zip (checagem de FR-017). */
    private static final byte[] MAGIC_ZIP = {0x50, 0x4B, 0x03, 0x04};

    private final VersaoRuntimeRepository versaoRepository;
    private final VersaoRuntimeArquivoRepository arquivoRepository;
    private final ApkBinarioRepository binarioRepository;

    public VersaoAppService(VersaoRuntimeRepository versaoRepository,
                            VersaoRuntimeArquivoRepository arquivoRepository,
                            ApkBinarioRepository binarioRepository) {
        this.versaoRepository = versaoRepository;
        this.arquivoRepository = arquivoRepository;
        this.binarioRepository = binarioRepository;
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
     * Publica uma versão e a torna ativa. Validações da FR-017 acontecem
     * antes de qualquer escrita.
     */
    @Transactional
    public VersaoRuntime publicar(PublicarVersaoRequest request, MultipartFile arquivo,
                                  PlataformaAppEnum plataforma, String publicadaPorId) {
        return publicar(request, FonteApk.de(arquivo), plataforma, publicadaPorId);
    }

    @Transactional
    public VersaoRuntime publicar(PublicarVersaoRequest request, FonteApk arquivo,
                                  PlataformaAppEnum plataforma, String publicadaPorId) {
        validarArquivo(arquivo);
        validarMetadados(request, plataforma);

        Hashes hashes = calcularHashes(arquivo);

        VersaoRuntime versao = new VersaoRuntime(plataforma, request.versao().trim(),
                request.versionCode(), request.runtimeVersion().trim(),
                request.notas(), request.versionCodeMinimo(), publicadaPorId);
        versao.setTamanhoBytes(hashes.tamanho());
        versao.setSha256(hashes.sha256());
        versao.setMd5(hashes.md5());

        versaoRepository.desativarTodas(plataforma);
        versao.setAtiva(true);
        versaoRepository.save(versao);
        versaoRepository.flush();

        arquivoRepository.save(new VersaoRuntimeArquivo(versao.getId()));
        arquivoRepository.flush();
        binarioRepository.gravar(versao.getId(), abrir(arquivo), hashes.tamanho());

        return versao;
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

    private void validarArquivo(FonteApk arquivo) {
        if (arquivo == null || arquivo.vazio()) {
            throw new IllegalArgumentException("Envie o arquivo APK.");
        }
        String nome = arquivo.nome();
        if (nome == null || !nome.toLowerCase().endsWith(".apk")) {
            throw new IllegalArgumentException("O arquivo precisa ser um APK (.apk).");
        }
        byte[] cabecalho = new byte[MAGIC_ZIP.length];
        try (InputStream in = arquivo.abrir()) {
            int lidos = in.readNBytes(cabecalho, 0, cabecalho.length);
            if (lidos < MAGIC_ZIP.length || !java.util.Arrays.equals(cabecalho, MAGIC_ZIP)) {
                throw new IllegalArgumentException(
                        "O arquivo não parece um APK válido (assinatura de conteúdo inesperada).");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private void validarMetadados(PublicarVersaoRequest request, PlataformaAppEnum plataforma) {
        if (request.versionCodeMinimo() > request.versionCode()) {
            throw new IllegalArgumentException(
                    "O versionCode mínimo suportado (%d) não pode ser maior que o da própria versão (%d)."
                            .formatted(request.versionCodeMinimo(), request.versionCode()));
        }
        versaoRepository.maiorVersionCode(plataforma).ifPresent(maior -> {
            if (request.versionCode() <= maior) {
                throw new IllegalArgumentException(
                        ("versionCode %d é menor ou igual ao já publicado (%d). O Android recusa instalar "
                                + "por cima de uma versão igual ou mais nova.")
                                .formatted(request.versionCode(), maior));
            }
        });
    }

    // ---------- hashes (D4) ----------

    /**
     * Duas passadas pelo arquivo: aqui só os digests e o tamanho; a gravação lê
     * de novo. O multipart do Spring escreve o upload em disco, então reabrir o
     * stream é barato — e o código fica sem o acoplamento de calcular hash
     * enquanto o driver consome o mesmo stream.
     */
    private Hashes calcularHashes(FonteApk arquivo) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            long tamanho = 0;
            try (InputStream base = arquivo.abrir();
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

    private InputStream abrir(FonteApk arquivo) {
        try {
            return arquivo.abrir();
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
