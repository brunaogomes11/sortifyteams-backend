package com.gomesdev.sortifyteams.domain.racha;

import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.domain.racha.request.RachaRequest;
import com.gomesdev.sortifyteams.enums.CriterioEmpateEnum;
import com.gomesdev.sortifyteams.enums.StatusRachaEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "tb_racha")
@Schema(description = "Partida informal organizada no app")
public class Racha {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    @Schema(description = "Identificador único ULID do racha")
    private String id;

    @Column(name = "esporte_id", nullable = false, length = 26)
    @Schema(description = "ID ULID do esporte")
    private String esporteId;

    @Column(name = "organizador_id", nullable = false, length = 26)
    @Schema(description = "ID ULID do organizador")
    private String organizadorId;

    @Column(name = "quadra_id", length = 26)
    @Schema(description = "ID ULID da quadra reservada (opcional)")
    private String quadraId;

    @Column(name = "data")
    @Schema(description = "Data do jogo")
    private LocalDate data;

    @Column(name = "horario")
    @Schema(description = "Horário do jogo")
    private LocalTime horario;

    @Column(name = "local", length = 140)
    @Schema(description = "Local combinado do racha, em texto livre (onde vai ser)")
    private String local;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Schema(description = "Status do racha")
    private StatusRachaEnum status = StatusRachaEnum.ABERTO;

    @Column(name = "limite_vagas")
    @Schema(description = "Limite opcional de participantes (C9)")
    private Integer limiteVagas;

    @Column(name = "token_convite", nullable = false, unique = true, length = 26)
    @Schema(description = "Token do link de convite (deep link — C9)")
    private String tokenConvite;

    @Column(name = "balancear_nivel", nullable = false)
    @Schema(description = "Se o sorteio balanceia por nível técnico")
    private boolean balancearNivel;

    @Column(name = "usa_nivel_tecnico", nullable = false, columnDefinition = "boolean not null default true")
    @Schema(description = "Se o racha usa nível técnico dos jogadores (estrelas na UI e balanceamento)")
    private boolean usaNivelTecnico = true;

    @Column(name = "qtd_times", nullable = false)
    @Schema(description = "Quantidade de times do sorteio")
    private int qtdTimes = 2;

    @Column(name = "incluir_goleiros_no_sorteio", nullable = false,
            columnDefinition = "boolean not null default true")
    @Schema(description = "Se os goleiros entram no sorteio dos times (um por time). "
            + "Desligado: os goleiros ficam num grupo à parte (sem número), fora dos times numerados.")
    private boolean incluirGoleirosNoSorteio = true;

    @Column(name = "duracao_partida_seg")
    @Schema(description = "Duração da partida em segundos, registrada pelo cronômetro (C1)")
    private Integer duracaoPartidaSeg;

    @Column(name = "iniciado_em")
    @Schema(description = "Momento em que o racha entrou ao vivo (EM_ANDAMENTO)")
    private LocalDateTime iniciadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterio_empate_zero")
    @Schema(description = "Critério de desempate para partidas 0x0")
    private CriterioEmpateEnum criterioEmpateZero;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterio_empate_gols")
    @Schema(description = "Critério de desempate para empates com gols (1x1, 2x2...)")
    private CriterioEmpateEnum criterioEmpateGols;

    @Column(name = "publico", columnDefinition = "boolean not null default false")
    @Schema(description = "Se o racha aparece na busca pública de rachas próximos")
    private boolean publico = false;

    @Column(name = "latitude")
    @Schema(description = "Latitude do racha (herdada da quadra ou geocodificada do local)")
    private Double latitude;

    @Column(name = "longitude")
    @Schema(description = "Longitude do racha (herdada da quadra ou geocodificada do local)")
    private Double longitude;

    @Column(name = "cidade", length = 120)
    @Schema(description = "Cidade do racha (para filtro por cidade quando sem GPS)")
    private String cidade;

    @Column(name = "criado_em", nullable = false)
    @Schema(description = "Data/hora de criação")
    private LocalDateTime criadoEm;

    public Racha() {
    }

    public Racha(RachaRequest request, String organizadorId) {
        this.esporteId = request.esporteId();
        this.organizadorId = organizadorId;
        this.data = request.data();
        this.horario = request.horario();
        this.local = request.local();
        this.limiteVagas = request.limiteVagas();
        this.usaNivelTecnico = request.usaNivelTecnico() == null || request.usaNivelTecnico();
        // Sem nível técnico não faz sentido balancear por nível.
        this.balancearNivel = this.usaNivelTecnico && Boolean.TRUE.equals(request.balancearNivel());
        this.qtdTimes = request.qtdTimes() != null ? request.qtdTimes() : 2;
        this.publico = Boolean.TRUE.equals(request.publico());
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.tokenConvite == null) {
            this.tokenConvite = UlidCreator.getUlid().toString();
        }
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEsporteId() { return esporteId; }
    public void setEsporteId(String esporteId) { this.esporteId = esporteId; }
    public String getOrganizadorId() { return organizadorId; }
    public void setOrganizadorId(String organizadorId) { this.organizadorId = organizadorId; }
    public String getQuadraId() { return quadraId; }
    public void setQuadraId(String quadraId) { this.quadraId = quadraId; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHorario() { return horario; }
    public void setHorario(LocalTime horario) { this.horario = horario; }
    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }
    public StatusRachaEnum getStatus() { return status; }
    public void setStatus(StatusRachaEnum status) { this.status = status; }
    public Integer getLimiteVagas() { return limiteVagas; }
    public void setLimiteVagas(Integer limiteVagas) { this.limiteVagas = limiteVagas; }
    public String getTokenConvite() { return tokenConvite; }
    public void setTokenConvite(String tokenConvite) { this.tokenConvite = tokenConvite; }
    public boolean isBalancearNivel() { return balancearNivel; }
    public void setBalancearNivel(boolean balancearNivel) { this.balancearNivel = balancearNivel; }
    public boolean isUsaNivelTecnico() { return usaNivelTecnico; }
    public void setUsaNivelTecnico(boolean usaNivelTecnico) { this.usaNivelTecnico = usaNivelTecnico; }
    public int getQtdTimes() { return qtdTimes; }
    public void setQtdTimes(int qtdTimes) { this.qtdTimes = qtdTimes; }
    public boolean isIncluirGoleirosNoSorteio() { return incluirGoleirosNoSorteio; }
    public void setIncluirGoleirosNoSorteio(boolean incluirGoleirosNoSorteio) { this.incluirGoleirosNoSorteio = incluirGoleirosNoSorteio; }
    public Integer getDuracaoPartidaSeg() { return duracaoPartidaSeg; }
    public void setDuracaoPartidaSeg(Integer duracaoPartidaSeg) { this.duracaoPartidaSeg = duracaoPartidaSeg; }
    public LocalDateTime getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(LocalDateTime iniciadoEm) { this.iniciadoEm = iniciadoEm; }
    public CriterioEmpateEnum getCriterioEmpateZero() { return criterioEmpateZero; }
    public void setCriterioEmpateZero(CriterioEmpateEnum criterioEmpateZero) { this.criterioEmpateZero = criterioEmpateZero; }
    public CriterioEmpateEnum getCriterioEmpateGols() { return criterioEmpateGols; }
    public void setCriterioEmpateGols(CriterioEmpateEnum criterioEmpateGols) { this.criterioEmpateGols = criterioEmpateGols; }
    public boolean isPublico() { return publico; }
    public void setPublico(boolean publico) { this.publico = publico; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Racha racha = (Racha) o;
        return Objects.equals(id, racha.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
