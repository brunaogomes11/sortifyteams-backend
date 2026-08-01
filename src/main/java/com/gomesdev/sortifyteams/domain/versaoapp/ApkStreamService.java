package com.gomesdev.sortifyteams.domain.versaoapp;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * Entrega do APK com suporte a retomada (spec 002, D3 — FR-010/FR-029).
 *
 * <p>Duas garantias que andam juntas:
 * <ul>
 *   <li><b>{@code Range}</b> permite continuar um download do ponto em que
 *       parou;</li>
 *   <li><b>{@code ETag} (sha256) + {@code If-Range}</b> garantem que o pedaço
 *       pedido é da mesma versão — se a publicada mudou, responde-se 200 com o
 *       arquivo novo inteiro e o cliente descarta o parcial sozinho (FR-011).</li>
 * </ul>
 *
 * <p>Em nenhum caminho — parcial ou completo — o binário inteiro entra na heap:
 * a resposta é escrita em fatias lidas do banco sob demanda.
 */
@Service
public class ApkStreamService {

    /** Fatia lida do banco por vez. Mantém a memória plana independente do APK. */
    static final int TAMANHO_FATIA = 256 * 1024;

    private static final MediaType TIPO_APK =
            MediaType.parseMediaType("application/vnd.android.package-archive");

    private final ApkBinarioRepository binarioRepository;

    public ApkStreamService(ApkBinarioRepository binarioRepository) {
        this.binarioRepository = binarioRepository;
    }

    public ResponseEntity<StreamingResponseBody> servir(VersaoRuntime versao,
                                                        String cabecalhoRange,
                                                        String cabecalhoIfRange) {
        if (!binarioRepository.existeBinario(versao.getId())) {
            throw new EntityNotFoundException(
                    "O binário da versão %s não está mais disponível.".formatted(versao.getVersao()));
        }

        long total = versao.getTamanhoBytes();
        String etag = "\"%s\"".formatted(versao.getSha256());

        Faixa faixa = resolverFaixa(cabecalhoRange, cabecalhoIfRange, etag, total);
        if (faixa == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TIPO_APK);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setETag(etag);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("zerinho-%s.apk".formatted(versao.getVersao()))
                .build());
        headers.setContentLength(faixa.tamanho());

        if (faixa.parcial()) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes %d-%d/%d".formatted(faixa.inicio(), faixa.fim(), total));
        }

        StreamingResponseBody corpo = saida -> {
            long restante = faixa.tamanho();
            long posicao = faixa.inicio();
            while (restante > 0) {
                int pedaco = (int) Math.min(TAMANHO_FATIA, restante);
                byte[] bytes = binarioRepository.lerFaixa(versao.getId(), posicao, pedaco);
                if (bytes == null || bytes.length == 0) {
                    break; // binário sumiu no meio do stream (expurgo concorrente)
                }
                saida.write(bytes);
                posicao += bytes.length;
                restante -= bytes.length;
            }
            saida.flush();
        };

        return new ResponseEntity<>(corpo, headers,
                faixa.parcial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }

    /**
     * Decide o que servir. Devolve {@code null} quando o {@code Range} é
     * insatisfazível (→ 416).
     */
    // Package-private de propósito: é a regra crítica da retomada (FR-010/FR-011)
    // e não depende de banco — dá para cobrir os limites em teste de unidade.
    Faixa resolverFaixa(String cabecalhoRange, String cabecalhoIfRange,
                        String etag, long total) {
        if (cabecalhoRange == null || cabecalhoRange.isBlank()) {
            return Faixa.completa(total);
        }
        // If-Range que não bate com o ETag atual: a versão publicada mudou, então
        // o parcial do cliente é de outro arquivo — devolve tudo (FR-011).
        if (cabecalhoIfRange != null && !cabecalhoIfRange.isBlank()
                && !etag.equals(cabecalhoIfRange.trim())) {
            return Faixa.completa(total);
        }
        List<HttpRange> faixas;
        try {
            faixas = HttpRange.parseRanges(cabecalhoRange);
        } catch (IllegalArgumentException e) {
            return Faixa.completa(total); // Range malformado: ignorar é resposta válida
        }
        if (faixas.isEmpty()) {
            return Faixa.completa(total);
        }
        if (faixas.size() > 1) {
            // Multipart/byteranges não é usado por nenhum cliente nosso; servir
            // o arquivo inteiro é resposta válida e evita um formato frágil.
            return Faixa.completa(total);
        }
        HttpRange faixa = faixas.get(0);
        long inicio = faixa.getRangeStart(total);
        long fim = faixa.getRangeEnd(total);
        if (inicio >= total || inicio > fim) {
            return null;
        }
        return new Faixa(inicio, fim, true);
    }

    record Faixa(long inicio, long fim, boolean parcial) {
        static Faixa completa(long total) {
            // fim = -1 quando total = 0 faz tamanho() dar 0, em vez de mentir 1
            // no Content-Length.
            return new Faixa(0, total - 1, false);
        }

        long tamanho() {
            return fim - inicio + 1;
        }
    }
}
