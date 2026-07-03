# Constituição do Projeto Racha

## Princípios Fundamentais

### I. Qualidade acima de velocidade
Preferimos menos features bem feitas a muitas quebradas. Uma feature só é
considerada pronta quando funciona de ponta a ponta (app ↔ API ↔ banco),
tem tratamento de erro adequado e cobre os casos-limite mapeados na spec.
Features incompletas não entram na branch principal escondidas atrás de
"depois arrumamos".

### II. UX mobile consistente
Todas as telas do app seguem a mesma linguagem visual e de interação,
definida pelo design system em construção (redesign em paralelo das telas
atuais). Nenhuma tela nova inventa padrões próprios de navegação, botões,
espaçamento ou feedback. Enquanto o design system não cobre um caso, a tela
segue o padrão da tela mais próxima já redesenhada e o gap é registrado.

### III. Privacidade de dados sensíveis (INEGOCIÁVEL)
Nome completo, e-mail e contato de usuários nunca são expostos publicamente
sem consentimento explícito. Endpoints que listam pessoas (participantes de
racha, reservas na agenda do dono de quadra) retornam apenas o mínimo
necessário para aquele contexto. Senhas sempre com hash BCrypt; tokens JWT
nunca em logs.

### IV. Testes obrigatórios para regras de negócio críticas (INEGOCIÁVEL)
Regras de negócio críticas exigem testes automatizados escritos junto com a
implementação (não depois):
- Algoritmo de sorteio de times (balanceamento de nível, distribuição de
  goleiros, excedente de jogadores, mínimos por esporte).
- Cálculo de preço de reserva de quadra (preço por horário, múltiplos
  horários, total).
- Conflito de reserva de horário (dois organizadores no mesmo slot).
Alterações nessas regras sem teste correspondente são rejeitadas em review.

### V. Performance percebida
Telas devem responder em <300ms percebido: feedback imediato (skeleton,
spinner, optimistic update) mesmo quando a rede demora. Toda lista
potencialmente longa (quadras, jogadores, reservas) usa virtualização
(FlatList/FlashList) — nunca ScrollView com map.

### VI. Código incremental e revisável
Sem features gigantes em um único commit/PR. Cada tarefa do plano gera uma
mudança pequena, coesa e revisável, com o app compilando e os testes
passando em cada incremento. Migrações de schema acompanham o incremento
que as exige, nunca "em lote no final".

## Restrições Adicionais
- Escopo do MVP fechado: nada de campeonato/torneio ou ranking histórico.
- Stack fixada (ver plano técnico): React Native (iOS/Android), Spring Boot
  com API REST em `/api/**` (JWT) e painel admin Thymeleaf em `/admin/**`
  (sessão). Mudanças de stack exigem emenda a esta constituição.
- Banco relacional: H2 (ou similar) em desenvolvimento, PostgreSQL em produção.

## Fluxo de Desenvolvimento
1. Toda feature nasce de uma spec em `specs/<feature>/spec.md` com os
   `[PRECISA CLARIFICAR]` resolvidos antes do plano técnico.
2. O plano (`plan.md`) compara alternativas quando há decisão em aberto e
   registra a justificativa da escolha.
3. As tarefas (`tasks.md`) são incrementais e ordenadas por dependência;
   regras críticas (Princípio IV) têm a tarefa de teste explícita.
4. Review verifica aderência a esta constituição antes de mérito funcional.

## Governança
Esta constituição prevalece sobre preferências individuais e atalhos de
prazo. Emendas exigem registro no histórico do repositório com justificativa
e atualização da versão abaixo. Todo PR/review deve verificar conformidade;
complexidade adicional precisa ser justificada na spec ou no plano.

**Versão**: 1.0.0 | **Ratificada**: 2026-07-02 | **Última emenda**: 2026-07-02
