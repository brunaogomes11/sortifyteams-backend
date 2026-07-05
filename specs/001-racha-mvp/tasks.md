# Tarefas: Racha — MVP

**Entrada**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)
**Convenções**: `[P]` = pode rodar em paralelo com as vizinhas (arquivos
diferentes, sem dependência). `(BE)` = backend Spring
(`F:\PROJETOS_DEV\Spring\sortifyteams`), `(APP)` = mobile
(`F:\PROJETOS_DEV\React Native\sortify-teams`). Cada tarefa é um incremento
revisável: compila, testes verdes, commit próprio (Constituição VI).
Tarefas de regra crítica incluem os testes na própria tarefa (Constituição IV).

## Fase 0 — Fundações

- [x] **T001** (BE) `git init` + `.gitignore` (target/, `.env`, uploads/) +
  commit inicial do scaffold.
- [x] **T002** (BE) Adicionar deps ao `pom.xml` espelhando o
  photographer-manager: JJWT 0.12.6 (api/impl/jackson), SpringDoc 3.0.3,
  ulid-creator, `spring-boot-starter-thymeleaf`,
  `spring-boot-starter-validation`, Testcontainers (só em testes).
- [x] **T003** (BE) `application.properties` com
  `spring.config.import=optional:file:.env[.properties]` e datasource via
  `${DB_HOST}/${DB_PORT}/${DB_NAME}/${DB_USERNAME}/${DB_PASSWORD}` (D7);
  criar `.env` local e `.env.example` versionado (DB_*, `JWT_SECRET`,
  `STORAGE_PATH`, `APP_DEEP_LINK_BASE`).
- [x] **T004** (BE) Padrão de persistência do photographer-manager:
  `ddl-auto=update` (schema pelo Hibernate), `data.sql` idempotente para
  seeds, Spring Session com `initialize-schema=always`, `security/JwtService`
  (JJWT) e `config/ErrorDetails`. (Seed dos esportes entra na T014, junto com
  a entidade.)
- [x] **T005** (BE) `config/`: duas `SecurityFilterChain` (D1) — `/api/**`
  stateless (JWT, tudo negado exceto `/api/auth/**`) e `/admin/**` (form
  login, sessão JDBC, CSRF). Teste MockMvc do isolamento das cadeias.
- [x] **T006** [P] (BE) `common/`: handler global de erros (ProblemDetail),
  DTO de paginação, validação padrão.
- [ ] **T007** (APP) `git init` (se necessário) + upgrade Expo SDK 49 → atual
  (React Navigation junto); remover `react-native-admob`; app abre e navega
  nas telas existentes.
- [x] **T008** (APP) Base de rede: axios com interceptor Bearer + fila de
  refresh no 401 (D3), TanStack Query, `expo-secure-store` para tokens,
  config de `API_BASE_URL` por ambiente; estrutura de pastas por feature.

## Fase 1 — Autenticação (Fluxo 1)

- [x] **T009** (BE) Entidades/repos `usuario`, `refresh_token`, `push_token`.
- [x] **T010** (BE) `POST /api/auth/registro` (papel JOGADOR|DONO_QUADRA,
  BCrypt, dono nasce PENDENTE) e `POST /api/auth/login` (403 com motivo para
  PENDENTE/REJEITADO — FR-003). Testes: registro, login ok, dono pendente
  negado, username/email duplicados.
- [x] **T011** (BE) Emissão JWT (access 15min) + refresh opaco rotativo em
  tabela (D3): `POST /api/auth/refresh`, detecção de reuso invalida a família.
  Testes de rotação e reuso.
- [x] **T012** [P] (BE) `POST /api/auth/logout` (revoga refresh) e
  `POST /api/auth/reenviar-solicitacao` (C13 — REJEITADO → PENDENTE).
- [x] **T013** (APP) Telas: Login, Cadastro (escolha de papel), Aguardando
  Aprovação (nova — design system) com estado de rejeitado + reenviar (C13);
  roteamento por papel/status após login.

## Fase 2 — Racha + Sorteio (Fluxo 3, núcleo)

- [x] **T014** (BE) Entidades/repos `esporte`, `racha`, `participante_racha`,
  `time_racha` (IDs ULID, tabelas `tb_*`); seed dos 10 esportes via `data.sql`
  (`ON CONFLICT DO NOTHING`); `GET /api/esportes`.
- [x] **T015** (BE) `POST /api/rachas`, `GET /api/rachas` (paginado),
  `GET /api/rachas/{id}`; participantes: adicionar (avulso ou `usuario_id`),
  remover; `GET /api/usuarios/busca?q=` retornando o mínimo (FR-016/C3).
- [x] **T016** (BE) **`SorteioService` puro + testes unitários na mesma task**
  (FR-007): balanceamento por nível, goleiros um por time antes dos demais,
  excedente C5 (menor nível médio / aleatório, diferença máx. 1), mínimo C6,
  determinismo com seed injetável. Endpoint `POST /api/rachas/{id}/sorteio`
  persiste os times.
- [x] **T017** [P] (BE) `POST /api/rachas/{id}/concluir`
  (`duracao_partida_seg?` — C1) e transições de status com testes.
- [x] **T018** (APP) Fluxo Criar Racha: grid de esportes, adicionar jogadores
  (avulso + busca de usuários), config de sorteio (nº times, balancear).
  Listas virtualizadas (FlashList).
- [x] **T019** (APP) Tela Resultado do Sorteio (C4 — desenhar pelo design
  system) + refazer sorteio.
- [ ] **T020** [P] (APP) Cronômetro vinculado ao racha (C1): reaproveitar
  timer atual, entrada pela Home (escolhe racha) e pelo detalhe; ao concluir,
  envia duração.
- [x] **T021** (APP) Home nova: atalhos + tabs Jogador (Home, Rachas, Quadras
  — C2); lista Meus Rachas.

## Fase 3 — Painel Admin (Fluxo 8)

- [x] **T022** (BE) Seed de admin (migração ou propriedade de config), form login
  `/admin/login` com sessão + CSRF; layout Thymeleaf base com Tailwind CDN.
- [x] **T023** (BE) `/admin/solicitacoes`: lista PENDENTES, ações
  Aprovar/Rejeitar (POST com CSRF). Teste MockMvc com sessão.
- [x] **T024** (BE) Núcleo de notificações (D5): entidade `notificacao`,
  registro de `push_token` (`POST /api/perfil/push-token`), cliente Expo Push;
  notificar aprovação/rejeição de dono. `GET /api/notificacoes` +
  `PUT /api/notificacoes/{id}/lida`.

## Fase 4 — Quadras do Dono (Fluxo 7)

- [x] **T025** (BE) Entidades/repos `quadra`, `quadra_foto`, `quadra_horario`;
  CRUD `/api/dono/quadras` (só DONO_QUADRA aprovado, só as próprias — 1:N C12).
- [x] **T026** (BE) `StorageService` + `LocalDiskStorage` (D4): upload
  multipart de fotos de quadra e de perfil, validação tipo/tamanho, servir via
  URL.
- [x] **T027** (BE) Grade semanal: `PUT /api/dono/quadras/{id}/horarios`
  (dia_semana, hora_inicio/fim, preço) com validação de sobreposição + testes.
- [x] **T028** (APP) Navegação do Dono (Minhas Quadras, Agenda — Fluxo 7);
  telas Minhas Quadras, Cadastrar/Editar Quadra (fotos, grade semanal, preços).

## Fase 5 — Reserva de Quadra (Fluxo 4)

- [x] **T029** (BE) `GET /api/quadras` (filtros + paginação),
  `GET /api/quadras/{id}`, `GET /api/quadras/{id}/disponibilidade?data=`
  (grade − reservas confirmadas).
- [x] **T030** (BE) **Reserva com testes na mesma task** (FR-008/009):
  `POST /api/reservas` calcula `preco_total` (testes unitários: 1 e N
  horários), transação + violação da `UNIQUE (quadra_horario_id, data)` →
  HTTP 409 com horários alternativos; cancelamento remove os slots de
  `tb_reserva_horario` (libera o horário, histórico fica em `tb_reserva`).
  Teste de integração Testcontainers com 2 requisições concorrentes
  (1 sucesso, 1 conflito — C8).
- [x] **T031** (APP) Telas: Lista de Quadras (filtros, FlashList), Detalhe
  (fotos, contato), Data/Horário (múltipla seleção, preço por horário e
  total), confirmação + tratamento do 409 com alternativas.

## Fase 6 — Gestão do Racha (Fluxo 5)

- [x] **T032** (BE) Convite (C9): `token_convite`, `GET /api/convites/{token}`
  (dados mínimos — FR-016), `POST /api/convites/{token}/entrar` respeitando
  `limite_vagas`; sair do racha. Testes de limite e reentrada.
- [x] **T033** (BE) Cancelamentos C10 com testes: `DELETE /api/rachas/{id}`
  cancela reserva + notifica dono; `DELETE /api/dono/reservas/{id}` notifica
  organizador e participantes cadastrados.
- [x] **T034** (BE) `GET /api/dono/agenda?de=&ate=` (data, horário, quem
  reservou — mínimo necessário, status).
- [x] **T035** (APP) Tela Gerenciar Racha (visões organizador × participante),
  Compartilhar (share sheet com deep link `racha://convite/<token>` via
  expo-linking + rota de entrada), cancelar/sair com confirmação.
- [x] **T036** [P] (APP) Tela Agenda do Dono com cancelamento de reserva.

## Fase 7 — Perfil, Dashboard e Lembretes

- [x] **T037** (BE) Perfil (FR-012): `GET/PUT /api/perfil`, foto, contador de
  rachas CONCLUÍDOS, esporte preferido = moda do histórico com override manual
  (C11) + testes do cálculo.
- [x] **T038** (APP) Tela Perfil (foto, contadores, esporte preferido com
  edição) + central de notificações (badge, lista, marcar lida).
- [x] **T039** (BE) Dashboard admin (C14): view SQL de rachas concluídos por
  quadra + página `/admin/dashboard`, estrutura pronta para expandir por
  horário.
- [x] **T040** [P] (BE) Lembrete de racha via `@Scheduled` (notificação in-app
  + push no dia do jogo).

## Fase 8 — Endurecimento

- [ ] **T041** (BE) Auditoria de privacidade (FR-016/Constituição III): revisar
  todos os DTOs de resposta contra exposição de e-mail/contato fora de
  contexto; garantir que JWT/senha nunca aparecem em logs.
- [ ] **T042** [P] (APP) Passada de performance: virtualização em todas as
  listas, skeletons/optimistic updates (<300ms percebido), estados de
  erro/vazio consistentes.
- [ ] **T043** Validação final: executar os 5 critérios de aceitação da spec
  de ponta a ponta e registrar evidências.

## Dependências principais

- T001–T008 antes de tudo (T007/T008 paralelos ao backend).
- Fase 1 antes de todas as seguintes; T024 (notificações) antes de T033/T040.
- T025–T027 antes de T029–T030; T030 antes de T031; T032–T034 antes de T035–T036.
- Design system: T013 (aguardando aprovação) e T019 (resultado do sorteio)
  dependem do import do Claude Design ou de desenhar direto pelo padrão das
  telas redesenhadas.
