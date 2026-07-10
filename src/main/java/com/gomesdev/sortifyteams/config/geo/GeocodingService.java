package com.gomesdev.sortifyteams.config.geo;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Geocodificação de endereços via Nominatim (OpenStreetMap, gratuito).
 *
 * <p>É best-effort: qualquer falha (rede fora, endereço não encontrado, serviço
 * indisponível) devolve {@link Optional#empty()} — nunca lança para não quebrar
 * o cadastro que a invocou (quadra do dono ou local do racha).</p>
 *
 * <p>Respeita a política de uso do Nominatim: envia um {@code User-Agent}
 * identificável. Em testes fica desligada via {@code app.geocoding.enabled=false}.</p>
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final String[] CHAVES_CIDADE = {"city", "town", "village", "municipality", "county"};

    private final boolean enabled;
    private final RestClient client;

    public GeocodingService(
            @Value("${app.geocoding.enabled:true}") boolean enabled,
            @Value("${app.geocoding.url:https://nominatim.openstreetmap.org/search}") String url,
            @Value("${app.geocoding.user-agent:SortifyTeams/1.0 (contato@sortifyteams.app)}") String userAgent) {
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("User-Agent", userAgent)
                .requestFactory(factory)
                .build();
    }

    /** Resolve um endereço/local em coordenadas + cidade. Vazio quando não dá. */
    public Optional<GeocodeResult> geocodificar(String endereco) {
        if (!enabled || endereco == null || endereco.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode resposta = client.get()
                    .uri(uri -> uri
                            .queryParam("q", endereco)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("addressdetails", 1)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (resposta == null || !resposta.isArray() || resposta.isEmpty()) {
                return Optional.empty();
            }
            JsonNode primeiro = resposta.get(0);
            JsonNode lat = primeiro.get("lat");
            JsonNode lon = primeiro.get("lon");
            if (lat == null || lon == null) {
                return Optional.empty();
            }
            return Optional.of(new GeocodeResult(
                    lat.asDouble(), lon.asDouble(), extrairCidade(primeiro.get("address"))));
        } catch (Exception e) {
            log.warn("Falha ao geocodificar '{}': {}", endereco, e.getMessage());
            return Optional.empty();
        }
    }

    private String extrairCidade(JsonNode address) {
        if (address == null) {
            return null;
        }
        for (String chave : CHAVES_CIDADE) {
            JsonNode valor = address.get(chave);
            if (valor != null && !valor.asText().isBlank()) {
                return valor.asText();
            }
        }
        return null;
    }
}
