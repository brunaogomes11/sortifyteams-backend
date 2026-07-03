# Especificação de Feature: Racha — MVP

**Branch da feature**: `001-racha-mvp`
**Criada em**: 2026-07-03
**Status**: Clarificada — pronta para /plan
**Entrada**: descrição de produto fornecida pelo usuário (app mobile nativo para
organizar partidas informais de esportes coletivos)

## Visão Geral

Racha é um app mobile (iOS/Android) para organizar partidas informais de
esportes coletivos entre amigos/conhecidos: escolher o esporte, cadastrar quem
vai jogar, sortear times equilibrados e, opcionalmente, reservar uma quadra
para o jogo. Complementarmente, donos de quadra usam o mesmo app como
organizador de horários das suas quadras, e admins aprovam esses donos por um
painel web separado.

**Fora do escopo do MVP** (explícito): qualquer funcionalidade de
campeonato/torneio (abas, telas, regras); ranking entre rachas/times ao longo
do tempo; pagamento de reserva dentro do app (decisão C7).

## Clarificações

### Sessão 2026-07-03
- **C1** — Cronômetro: **vinculado a um racha específico**. O atalho da Home
  leva à escolha de um racha do usuário; a duração da partida pode ser
  registrada no racha.
- **C2** — Espaço da aba "Campeonatos": ocupado pela aba **"Quadras"**.
- **C3** — Jogadores do racha: **ambos** — nome avulso por padrão, com busca
  opcional de usuários cadastrados para convidar.
- **C4** — Tela de resultado do sorteio: **desenhar seguindo o design system**
  (sem wireframe prévio).
- **C5** — Excedente do sorteio: **quando "balancear nível técnico" estiver
  ativo**, o excedente vai para os times de menor nível médio; diferença máxima
  de 1 jogador entre times. Sem balanceamento, distribuição aleatória (mantendo
  a diferença máxima de 1).
- **C6** — Mínimo para sortear: **`jogadores_minimos_por_time × nº de times`**,
  configurado por esporte.
- **C7** — Pagamento: **fora do MVP**. O app registra a reserva e exibe o
  contato da quadra; pagamento combinado fora (pix direto, presencial).
- **C8** — Conflito de horário: **lock transacional + constraint única**
  (quadra, data, horário). O primeiro que confirma leva; o segundo recebe erro
  claro com sugestão de outros horários.
- **C9** — Compartilhar: **link de convite com deep link** para entrar no
  racha; **limite de vagas opcional** definido pelo organizador.
- **C10** — Cancelamentos: racha cancelado **cancela a reserva junto e
  notifica o dono da quadra**; se o **dono** cancelar a reserva, **os
  jogadores do racha são avisados**.
- **C11** — Esporte preferido: **calculado do histórico**, com **override
  manual** no perfil.
- **C12** — Dono de quadra **pode ter várias quadras** (1:N).
- **C13** — Cadastro rejeitado: **pode reenviar a solicitação** (status volta
  a PENDENTE); sem bloqueio permanente de e-mail/username.
- **C14** — Painel admin no MVP: **aprovação de donos + dashboard** com
  quantidade de rachas concluídos por quadra, desenhado para expandir depois
  (ex.: quais horários são mais usados).

## Personas

- **Organizador**: cria o racha, adiciona jogadores, configura o sorteio,
  reserva a quadra, pode cancelar o racha.
- **Participante**: entra num racha existente (por convite/deep link), vê os
  detalhes (quadra, horário, outros participantes), pode sair do racha.
- **Dono de Quadra**: cadastra e gerencia suas próprias quadras (1:N); usa o
  app como organizador de horários (define horários disponíveis, acompanha
  reservas). Acesso condicionado à aprovação de um admin.
- **Admin**: aprova/rejeita donos de quadra e acompanha o dashboard de uso.
  Não usa o app mobile — atua pelo painel web (Fluxo 8).

## Cenários de Usuário

### Fluxo 1 — Cadastro / Login
- Cadastro com Nome Completo, Usuário, e-mail e senha.
- Autenticação própria por usuário/senha, sem OAuth. Senha com hash BCrypt;
  API mobile via JWT (`Authorization: Bearer`).
- No cadastro, escolha do papel: **Jogador** (liberado imediatamente) ou
  **Dono de Quadra** (status PENDENTE; login só emite token após aprovação).
- Após cadastro/login (Jogador), usuário cai na Home.

### Fluxo 2 — Home
- Atalhos: Cronômetro (leva à escolha de um racha — C1), Sortear Times,
  Criar Racha.
- Navegação inferior do Jogador: Home, **Rachas**, **Quadras** (C2).
- Navegação do Dono de Quadra aprovado: **Minhas Quadras** e **Agenda**
  (Fluxo 7).

### Fluxo 3 — Criar Racha
1. **Escolher Esporte**: grid com Vôlei, Basquete, Handebol, Futebol, Tênis,
   Beach Tênis, Futsal, Futebol Society, Vôlei de Praia, Handebol de Praia.
   Cada esporte tem configuração própria (exige goleiro,
   jogadores_minimos_por_time).
2. **Adicionar Jogadores** (C3): nome avulso digitado (padrão) **ou** busca de
   usuário cadastrado para convidar. Para cada jogador: nível técnico e flag
   goleiro (quando o esporte exige). Lista com opção de remover.
3. **Configuração de Sorteio**: total de jogadores inseridos, quantidade de
   times, toggle "balancear nível técnico".
4. **Sortear**: gera os times.
   - Mínimo para sortear (C6): `jogadores_minimos_por_time × nº de times`.
   - Excedente (C5): com balanceamento ativo, sobras vão para os times de
     menor nível médio; sem balanceamento, aleatório. Diferença máxima de 1
     jogador entre times em ambos os casos.
   - Goleiros: distribuídos um por time antes dos demais, quando o esporte
     exige.
   - Tela de resultado desenhada conforme o design system (C4).

### Fluxo 4 — Reservar Quadra
1. **Lista de Quadras**: lista filtrável (virtualizada).
2. **Detalhe da Quadra**: fotos, endereço, contato, botão reservar.
3. **Data/Horário**: seleciona data, um ou mais horários disponíveis, vê preço
   por horário e total, confirma.
   - Sem pagamento no app (C7): a reserva é registrada e o contato da quadra
     fica visível para combinar o pagamento por fora.
   - Conflito (C8): constraint única (quadra, data, horário) + transação; o
     segundo organizador recebe erro claro.

### Fluxo 5 — Gerenciar Racha
- **Organizador**: quadra reservada, data/hora, participantes; ações: Sortear
  Times (refazer), Compartilhar (link de convite — C9), Cancelar racha.
- **Participante**: mesmos dados; ações: Compartilhar, Sair do racha.
- Convite (C9): deep link que abre o app na tela do racha; entrada respeita o
  limite de vagas opcional definido pelo organizador.
- Cancelamento (C10): cancelar o racha cancela a reserva e notifica o dono da
  quadra. Se o dono cancelar a reserva, os jogadores do racha são notificados.
- Cronômetro (C1): acessível no racha; duração da partida pode ser registrada.
- Racha tem ciclo de vida com status **CONCLUÍDO** (alimenta o contador de
  rachas participados, o esporte preferido — C11 — e o dashboard admin — C14).

### Fluxo 6 — Perfil do Jogador
- Foto de perfil, nome, contato, contador de rachas participados, esporte
  preferido (calculado do histórico, com override manual — C11).

### Fluxo 7 — Dono de Quadra (mesmo app React Native)
1. **Solicitar acesso**: cadastro do Fluxo 1 com papel "Dono de Quadra"; cai
   na tela "Aguardando aprovação" (nova, seguir design system).
   - Se rejeitado (C13): tela mostra a rejeição e permite reenviar a
     solicitação (status volta a PENDENTE).
2. Aprovado, no próximo login o app libera:
   - **Minhas Quadras**: lista das quadras dele (1:N — C12), botão
     "+ Cadastrar quadra".
   - **Cadastrar/Editar Quadra**: nome, endereço, contato, fotos, preço por
     horário, horários disponíveis (grade semanal recorrente).
   - **Agenda**: reservas nas suas quadras — data, horário, quem reservou,
     status; ação de cancelar reserva (notifica jogadores — C10).

### Fluxo 8 — Painel Administrativo (web, fora do app)
- Spring MVC + Thymeleaf + Tailwind (CDN), servido em `/admin/**`.
- Login usuário/senha com sessão (form login), sem JWT.
- **Aprovações**: lista de solicitações PENDENTES com Aprovar/Rejeitar.
- **Dashboard** (C14): quantidade de rachas concluídos por quadra; estrutura
  preparada para expandir (ex.: horários mais usados).

## Requisitos Funcionais

- **FR-001**: Cadastro com nome completo, usuário, e-mail e senha, com escolha
  de papel (Jogador | Dono de Quadra).
- **FR-002**: Autenticação própria usuário/senha (sem OAuth), BCrypt, API
  mobile via JWT.
- **FR-003**: Dono de Quadra nasce PENDENTE; login só emite token após
  aprovação; rejeitado pode reenviar solicitação (volta a PENDENTE).
- **FR-004**: Home com atalhos (Cronômetro→racha, Sortear Times, Criar Racha)
  e navegação inferior por papel (Jogador: Rachas/Quadras; Dono: Minhas
  Quadras/Agenda).
- **FR-005**: Criação de racha com um dos 10 esportes, cada um com
  configuração própria (exige_goleiro, jogadores_minimos_por_time).
- **FR-006**: Adição de jogadores por nome avulso ou busca/convite de usuário
  cadastrado, com nível técnico e flag goleiro; remoção da lista.
- **FR-007**: Sorteio de times conforme configuração (nº de times, balancear
  nível), com mínimo C6, excedente C5 e goleiros distribuídos primeiro.
  **Regra crítica — testes automatizados obrigatórios.**
- **FR-008**: Listar/filtrar quadras, ver detalhes e reservar 1+ horários numa
  data com preço por horário e total. **Cálculo de preço é regra crítica —
  testes obrigatórios.**
- **FR-009**: Impedir reserva dupla do mesmo (quadra, data, horário) via
  constraint única + transação, com erro claro ao perdedor. **Regra crítica —
  testes obrigatórios.**
- **FR-010**: Organizador refaz sorteio, compartilha (deep link de convite,
  limite de vagas opcional) e cancela o racha; participante compartilha e sai.
- **FR-011**: Cancelamento de racha cancela a reserva e notifica o dono;
  cancelamento de reserva pelo dono notifica os jogadores do racha.
- **FR-012**: Perfil com foto, nome, contato, contador de rachas participados
  e esporte preferido (histórico + override manual).
- **FR-013**: Dono aprovado gerencia quadras (CRUD com fotos, preço/hora,
  grade semanal) e vê a agenda de reservas das suas quadras.
- **FR-014**: Painel admin com sessão (form login), aprovações de donos e
  dashboard de rachas concluídos por quadra (expansível).
- **FR-015**: Cronômetro vinculado a um racha, com registro opcional da
  duração da partida.
- **FR-016**: Dados sensíveis (nome, contato) expostos apenas no mínimo
  necessário por contexto (constituição, Princípio III).

## Entidades-Chave (entrada do /plan)

- **Usuário**: id, nome_completo, username, email, senha_hash, role
  [JOGADOR | DONO_QUADRA | ADMIN], status [APROVADO | PENDENTE | REJEITADO],
  foto, contato, esporte_preferido_override?.
- **Esporte**: id, nome, ícone, exige_goleiro, jogadores_minimos_por_time.
- **Racha**: id, esporte_id, organizador_id, data, horário, quadra_id?,
  status [ABERTO | CONCLUIDO | CANCELADO], config_sorteio, limite_vagas?,
  token_convite, duracao_partida?.
- **ParticipanteRacha**: id, racha_id, usuario_id?, nome_avulso?,
  nivel_tecnico, e_goleiro, time_id?.
- **Time**: id, racha_id, nome/número.
- **Quadra**: id, nome, endereço, contato, fotos, preco_hora,
  horarios_disponiveis (grade semanal), dono_id.
- **Reserva**: id, quadra_id, racha_id, data, horarios[], preco_total,
  status [CONFIRMADA | CANCELADA_ORGANIZADOR | CANCELADA_DONO].
  Constraint única em (quadra_id, data, horário).
- **Notificação**: id, usuario_id, tipo, payload, lida, criada_em (suporta
  FR-011 e aprovação/rejeição de dono).

## Critérios de Aceitação de Alto Nível

1. Jogador se cadastra, loga, cria um racha de futsal com 12 jogadores
   (2 goleiros), sorteia 2 times balanceados por nível com um goleiro em cada,
   e compartilha o link de convite; um amigo entra pelo deep link até o limite
   de vagas.
2. Organizador reserva 2 horários seguidos numa quadra e o total exibido é a
   soma dos preços; um segundo organizador tentando os mesmos horários recebe
   erro claro.
3. Dono de quadra se cadastra, fica pendente (sem token), é aprovado pelo
   admin no painel web, cadastra uma quadra com grade semanal e vê na Agenda a
   reserva feita; ao cancelar a reserva, os jogadores são notificados.
4. Organizador cancela um racha com reserva: a reserva é cancelada e o dono
   notificado.
5. Racha concluído incrementa o contador do participante, entra no cálculo do
   esporte preferido e aparece no dashboard admin por quadra.

## Revisão & Aceite

- [x] Todos os [PRECISA CLARIFICAR] resolvidos (sessão 2026-07-03)
- [x] Requisitos testáveis e sem ambiguidade
- [x] Escopo delimitado (sem campeonato/torneio/ranking/pagamento)
- [ ] Plano técnico aprovado (/plan)
