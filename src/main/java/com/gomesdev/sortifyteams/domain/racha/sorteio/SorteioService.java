package com.gomesdev.sortifyteams.domain.racha.sorteio;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Algoritmo de sorteio de times (FR-007) — regra crítica da constituição.
 *
 * Lógica pura, sem dependência de banco: recebe os jogadores e devolve os
 * times. O Random é injetado por parâmetro para permitir testes
 * determinísticos com seed.
 *
 * Regras:
 * - Mínimo (C6): total de jogadores >= minimoPorTime × qtdTimes.
 * - Goleiros: com "incluir goleiros" ativo (padrão), quando o esporte exige são
 *   distribuídos um por time antes dos demais e os excedentes entram no bolo como
 *   jogadores de linha. Com "incluir goleiros" desligado, eles ficam de fora dos
 *   times numerados (só a linha é distribuída) — o grupo "Goleiros" é montado
 *   fora daqui, na camada de serviço.
 * - Balanceamento (C5): com "balancear nível" ativo, cada jogador (do maior
 *   nível para o menor) vai para o time com menos jogadores e, em empate,
 *   com a menor soma de nível — o excedente termina nos times de menor nível
 *   médio. Sem balanceamento, distribuição aleatória round-robin.
 * - Em ambos os casos a diferença de tamanho entre times é no máximo 1.
 */
@Service
public class SorteioService {

    public record JogadorSorteio(String participanteId, int nivelTecnico, boolean goleiro) {}

    public record TimeSorteado(int numero, List<JogadorSorteio> jogadores) {}

    public List<TimeSorteado> sortear(List<JogadorSorteio> jogadores,
                                      int qtdTimes,
                                      boolean balancearNivel,
                                      int minimoPorTime,
                                      Random random) {
        return sortear(jogadores, qtdTimes, balancearNivel, minimoPorTime, true, random);
    }

    public List<TimeSorteado> sortear(List<JogadorSorteio> jogadores,
                                      int qtdTimes,
                                      boolean balancearNivel,
                                      int minimoPorTime,
                                      boolean incluirGoleiros,
                                      Random random) {
        if (qtdTimes < 2) {
            throw new IllegalArgumentException("O sorteio precisa de pelo menos 2 times.");
        }

        // Com os goleiros fora do sorteio, só os jogadores de linha entram nos
        // times numerados; o grupo "Goleiros" é montado depois, no serviço.
        List<JogadorSorteio> aDistribuir = incluirGoleiros
                ? jogadores
                : jogadores.stream().filter(j -> !j.goleiro()).toList();

        int minimoTotal = minimoPorTime * qtdTimes;
        if (aDistribuir.size() < minimoTotal) {
            throw new IllegalArgumentException(
                    "Jogadores insuficientes para o sorteio: são necessários pelo menos %d (%d por time × %d times) e há %d."
                            .formatted(minimoTotal, minimoPorTime, qtdTimes, aDistribuir.size()));
        }

        List<List<JogadorSorteio>> times = new ArrayList<>();
        int[] somaNivel = new int[qtdTimes];
        for (int i = 0; i < qtdTimes; i++) {
            times.add(new ArrayList<>());
        }

        List<JogadorSorteio> restantes;
        if (incluirGoleiros) {
            // 1) Goleiros: um por time, em ordem aleatória; excedentes viram linha.
            List<JogadorSorteio> goleiros = new ArrayList<>(aDistribuir.stream().filter(JogadorSorteio::goleiro).toList());
            Collections.shuffle(goleiros, random);
            restantes = new ArrayList<>(aDistribuir.stream().filter(j -> !j.goleiro()).toList());
            for (int i = 0; i < goleiros.size(); i++) {
                if (i < qtdTimes) {
                    times.get(i).add(goleiros.get(i));
                    somaNivel[i] += goleiros.get(i).nivelTecnico();
                } else {
                    restantes.add(goleiros.get(i));
                }
            }
        } else {
            restantes = new ArrayList<>(aDistribuir);
        }

        // 2) Demais jogadores.
        Collections.shuffle(restantes, random);
        if (balancearNivel) {
            restantes.sort(Comparator.comparingInt(JogadorSorteio::nivelTecnico).reversed());
            for (JogadorSorteio jogador : restantes) {
                int alvo = escolherTime(times, somaNivel);
                times.get(alvo).add(jogador);
                somaNivel[alvo] += jogador.nivelTecnico();
            }
        } else {
            int inicio = random.nextInt(qtdTimes);
            for (int i = 0; i < restantes.size(); i++) {
                int alvo = menorTimeAPartirDe(times, (inicio + i) % qtdTimes);
                times.get(alvo).add(restantes.get(i));
            }
        }

        List<TimeSorteado> resultado = new ArrayList<>();
        for (int i = 0; i < qtdTimes; i++) {
            resultado.add(new TimeSorteado(i + 1, List.copyOf(times.get(i))));
        }
        return resultado;
    }

    /** Time com menos jogadores; empate → menor soma de nível; empate → primeiro. */
    private int escolherTime(List<List<JogadorSorteio>> times, int[] somaNivel) {
        int alvo = 0;
        for (int i = 1; i < times.size(); i++) {
            int cmp = Integer.compare(times.get(i).size(), times.get(alvo).size());
            if (cmp < 0 || (cmp == 0 && somaNivel[i] < somaNivel[alvo])) {
                alvo = i;
            }
        }
        return alvo;
    }

    /** Round-robin respeitando diferença máxima de 1 (goleiros já contam no tamanho). */
    private int menorTimeAPartirDe(List<List<JogadorSorteio>> times, int preferido) {
        int alvo = preferido;
        for (int i = 0; i < times.size(); i++) {
            int candidato = (preferido + i) % times.size();
            if (times.get(candidato).size() < times.get(alvo).size()) {
                alvo = candidato;
            }
        }
        return alvo;
    }
}
