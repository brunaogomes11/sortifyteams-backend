package com.gomesdev.sortifyteams.domain.racha.sorteio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gomesdev.sortifyteams.domain.racha.sorteio.SorteioService.JogadorSorteio;
import com.gomesdev.sortifyteams.domain.racha.sorteio.SorteioService.TimeSorteado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Testes unitários do algoritmo de sorteio (FR-007 — regra crítica, Constituição IV). */
class SorteioServiceTest {

    private final SorteioService service = new SorteioService();

    private List<JogadorSorteio> jogadores(int total, int goleiros, int... niveis) {
        List<JogadorSorteio> lista = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            int nivel = i < niveis.length ? niveis[i] : 3;
            lista.add(new JogadorSorteio("j" + i, nivel, i < goleiros));
        }
        return lista;
    }

    @Test
    @DisplayName("mínimo insuficiente lança erro claro (C6)")
    void minimoInsuficienteLancaErro() {
        assertThatThrownBy(() ->
                service.sortear(jogadores(7, 0), 2, false, 4, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos 8");
    }

    @Test
    @DisplayName("12 jogadores em 2 times → 6 em cada")
    void distribuicaoExata() {
        List<TimeSorteado> times = service.sortear(jogadores(12, 0), 2, false, 4, new Random(7));
        assertThat(times).hasSize(2);
        assertThat(times.get(0).jogadores()).hasSize(6);
        assertThat(times.get(1).jogadores()).hasSize(6);
    }

    @Test
    @DisplayName("excedente: diferença máxima de 1 jogador entre times (C5)")
    void excedenteDiferencaMaximaUm() {
        for (int total = 13; total <= 17; total++) {
            for (boolean balancear : new boolean[]{true, false}) {
                List<TimeSorteado> times = service.sortear(jogadores(total, 0), 3, balancear, 4, new Random(total));
                var tamanhos = times.stream().map(t -> t.jogadores().size()).toList();
                int max = tamanhos.stream().max(Integer::compare).orElseThrow();
                int min = tamanhos.stream().min(Integer::compare).orElseThrow();
                assertThat(max - min)
                        .as("total=%d balancear=%s tamanhos=%s", total, balancear, tamanhos)
                        .isLessThanOrEqualTo(1);
                assertThat(tamanhos.stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
            }
        }
    }

    @Test
    @DisplayName("goleiros distribuídos um por time antes dos demais")
    void goleirosUmPorTime() {
        // 10 jogadores, 2 goleiros, 2 times → exatamente 1 goleiro por time
        List<TimeSorteado> times = service.sortear(jogadores(10, 2), 2, true, 4, new Random(3));
        for (TimeSorteado time : times) {
            long goleiros = time.jogadores().stream().filter(JogadorSorteio::goleiro).count();
            assertThat(goleiros).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("goleiros excedentes entram como jogadores de linha")
    void goleirosExcedentesViramLinha() {
        // 12 jogadores, 5 goleiros, 2 times → cada time tem ao menos 1, total 12 distribuído 6/6
        List<TimeSorteado> times = service.sortear(jogadores(12, 5), 2, false, 4, new Random(11));
        assertThat(times.get(0).jogadores()).hasSize(6);
        assertThat(times.get(1).jogadores()).hasSize(6);
        for (TimeSorteado time : times) {
            long goleiros = time.jogadores().stream().filter(JogadorSorteio::goleiro).count();
            assertThat(goleiros).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("balancear nível: somas de nível próximas entre os times (C5)")
    void balanceamentoDeNivel() {
        // Níveis bem heterogêneos
        List<JogadorSorteio> jogadores = jogadores(12, 0, 5, 5, 5, 5, 4, 4, 2, 2, 1, 1, 1, 1);
        List<TimeSorteado> times = service.sortear(jogadores, 2, true, 4, new Random(42));

        List<Integer> somas = times.stream()
                .map(t -> t.jogadores().stream().mapToInt(JogadorSorteio::nivelTecnico).sum())
                .sorted(Comparator.naturalOrder())
                .toList();
        // Greedy garante diferença menor ou igual ao maior nível individual (5)
        assertThat(somas.get(1) - somas.get(0)).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("mesma seed produz o mesmo resultado (determinismo para testes)")
    void deterministicoComSeed() {
        List<JogadorSorteio> jogadores = jogadores(15, 3, 5, 3, 1, 4, 2, 5, 3, 2, 4, 1, 3, 5, 2, 4, 1);
        List<TimeSorteado> a = service.sortear(jogadores, 3, true, 4, new Random(99));
        List<TimeSorteado> b = service.sortear(jogadores, 3, true, 4, new Random(99));
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("menos de 2 times lança erro")
    void menosDeDoisTimesLancaErro() {
        assertThatThrownBy(() ->
                service.sortear(jogadores(10, 0), 1, false, 4, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
