package com.gomesdev.sortifyteams.domain.versaoapp.conteudo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomesdev.sortifyteams.config.storage.BaseUrlResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servidor de updates OTA (spec 002, D1-A).
 *
 * <p><b>Mantido deliberadamente isolado do domínio</b>: é a peça de maior
 * risco do plano — um manifesto fora do formato esperado deixa apps sem
 * conseguir atualizar. Se este caminho se mostrar problemático, a saída é
 * trocá-lo por um serviço gerenciado (D1-B) sem tocar em
 * {@link PacoteConteudoService}, nas entidades ou na checagem.
 *
 * <p>Responde o manifesto como {@code multipart/mixed} com uma parte chamada
 * {@code manifest}, que é o que o cliente de updates espera na versão 1 do
 * protocolo.
 */
@RestController
@RequestMapping("/api/app")
public class UpdatesProtocolController {

    private static final String BOUNDARY = "zerinho-update-boundary";

    private final PacoteConteudoService service;
    private final AssetBinarioRepository binarioRepository;
    private final BaseUrlResolver baseUrlResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdatesProtocolController(PacoteConteudoService service,
                                     AssetBinarioRepository binarioRepository,
                                     BaseUrlResolver baseUrlResolver) {
        this.service = service;
        this.binarioRepository = binarioRepository;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/manifest")
    public ResponseEntity<byte[]> manifest(
            @RequestHeader(value = "expo-runtime-version", required = false) String runtimeVersion,
            @RequestHeader(value = "expo-platform", required = false) String plataforma,
            @RequestHeader(value = "expo-current-update-id", required = false) String updateAtual) {

        Optional<PacoteConteudo> ativo = service.ativoDoRuntime(runtimeVersion);
        if (ativo.isEmpty()) {
            // Sem pacote para este runtime: 204 diz "nada a aplicar" sem erro.
            return ResponseEntity.noContent()
                    .header("expo-protocol-version", "1")
                    .header("expo-sfv-version", "0")
                    .build();
        }
        PacoteConteudo pacote = ativo.get();

        // O cliente já está no pacote publicado — evita reprocessar o manifesto.
        if (pacote.getUuidManifesto().equals(updateAtual)) {
            return ResponseEntity.noContent()
                    .header("expo-protocol-version", "1")
                    .header("expo-sfv-version", "0")
                    .build();
        }

        String corpo = montarMultipart(pacote);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "multipart/mixed; boundary=" + BOUNDARY));
        headers.set("expo-protocol-version", "1");
        headers.set("expo-sfv-version", "0");
        headers.setCacheControl("private, max-age=0");
        return ResponseEntity.ok().headers(headers).body(corpo.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/assets/{hash}")
    public ResponseEntity<byte[]> asset(@PathVariable String hash) {
        AssetConteudo asset = service.porHash(hash);
        byte[] conteudo = binarioRepository.ler(asset.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.ETAG, "\"" + asset.getHash() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(conteudo);
    }

    private String montarMultipart(PacoteConteudo pacote) {
        String manifesto = montarManifesto(pacote);
        return "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"manifest\"\r\n"
                + "Content-Type: application/json\r\n"
                + "\r\n"
                + manifesto + "\r\n"
                + "--" + BOUNDARY + "--\r\n";
    }

    private String montarManifesto(PacoteConteudo pacote) {
        List<PacoteAsset> assets = service.assetsDo(pacote.getId());
        String base = baseUrlResolver.resolve();

        Map<String, Object> launchAsset = null;
        List<Map<String, Object>> outros = new ArrayList<>();
        for (PacoteAsset vinculo : assets) {
            AssetConteudo asset = service.porId(vinculo.getAssetId());
            Map<String, Object> entrada = new LinkedHashMap<>();
            entrada.put("hash", asset.getHash());
            entrada.put("key", vinculo.getChave());
            entrada.put("contentType", asset.getContentType());
            entrada.put("fileExtension", "." + (vinculo.getExtensao() == null ? "" : vinculo.getExtensao()));
            entrada.put("url", base + "/api/app/assets/" + asset.getHash());
            if (asset.getId().equals(pacote.getLaunchAssetId())) {
                launchAsset = entrada;
            } else {
                outros.add(entrada);
            }
        }

        Map<String, Object> manifesto = new LinkedHashMap<>();
        manifesto.put("id", pacote.getUuidManifesto());
        manifesto.put("createdAt", pacote.getPublicadoEm().atOffset(ZoneOffset.UTC).toString());
        manifesto.put("runtimeVersion", pacote.getRuntimeVersion());
        manifesto.put("launchAsset", launchAsset);
        manifesto.put("assets", outros);
        manifesto.put("metadata", Map.of());
        manifesto.put("extra", Map.of());

        try {
            return objectMapper.writeValueAsString(manifesto);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Falha ao montar o manifesto de update.", e);
        }
    }

}
