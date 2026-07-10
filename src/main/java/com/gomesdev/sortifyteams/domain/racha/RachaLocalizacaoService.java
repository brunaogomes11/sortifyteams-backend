package com.gomesdev.sortifyteams.domain.racha;

import com.gomesdev.sortifyteams.config.geo.GeocodingService;
import com.gomesdev.sortifyteams.domain.quadra.Quadra;
import com.gomesdev.sortifyteams.domain.quadra.QuadraRepository;
import org.springframework.stereotype.Service;

/**
 * Resolve lat/long/cidade de um racha para a busca pública por proximidade
 * (só interessa a rachas públicos). Prioriza as coordenadas da quadra
 * reservada; sem quadra com coordenadas, geocodifica o texto livre
 * {@code local} (best-effort — pode não resolver).
 *
 * <p>Depende só de {@link QuadraRepository} e {@link GeocodingService} —
 * pode ser injetado tanto por {@link RachaService} quanto por
 * {@code ReservaService} sem criar ciclo de beans.</p>
 */
@Service
public class RachaLocalizacaoService {

    private final QuadraRepository quadraRepository;
    private final GeocodingService geocodingService;

    public RachaLocalizacaoService(QuadraRepository quadraRepository, GeocodingService geocodingService) {
        this.quadraRepository = quadraRepository;
        this.geocodingService = geocodingService;
    }

    /** Limpa as coordenadas atuais e tenta resolvê-las de novo (quadra ou local). */
    public void resolver(Racha racha) {
        racha.setLatitude(null);
        racha.setLongitude(null);
        racha.setCidade(null);

        if (racha.getQuadraId() != null) {
            Quadra quadra = quadraRepository.findById(racha.getQuadraId()).orElse(null);
            if (quadra != null && quadra.getLatitude() != null) {
                racha.setLatitude(quadra.getLatitude());
                racha.setLongitude(quadra.getLongitude());
                racha.setCidade(quadra.getCidade());
                return;
            }
        }
        if (racha.getLocal() != null && !racha.getLocal().isBlank()) {
            geocodingService.geocodificar(racha.getLocal()).ifPresent(r -> {
                racha.setLatitude(r.latitude());
                racha.setLongitude(r.longitude());
                racha.setCidade(r.cidade());
            });
        }
    }
}
