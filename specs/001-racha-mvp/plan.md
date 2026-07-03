# Plano de Implementação: Racha — MVP

**Branch**: `001-racha-mvp` | **Data**: 2026-07-03 | **Spec**: [spec.md](./spec.md)
**Status**: Proposto — aguardando aprovação para /tasks

## Contexto Técnico (estado real dos repositórios)

**Backend** — `F:\PROJETOS_DEV\Spring\sortifyteams`
- Spring Boot **4.1.0** (Spring Framework 7 / Spring Security 7), Java **25**.
- Já no pom: `data-jpa`, `security`, `session-jdbc`, `webmvc`, `postgresql`,
  devtools e os starters de teste correspondentes.
- **Diretriz do usuário: a tecnologia do backend espelha exatamente o
  photographer-manager (framelio-backend)**: JJWT 0.12.6, SpringDoc/Swagger,
  ULID (ulid-creator), `ddl-auto=update` + `data.sql` (sem Flyway), conexão
  JDBC via `.env`. Adicionados também `thymeleaf` (painel admin) e
  `validation`; Testcontainers fica restrito aos testes (infra de teste, não
  muda a tecnologia de produção).
- Sem git init ainda — inicializar no primeiro incremento.

**Mobile** — `F:\PROJETOS_DEV\React Native\sortify-teams`
- Expo SDK **49** / RN **0.72** (2023 — desatualizado), React Navigation 6,
  styled-components, `react-native-countdown-circle-timer` e
  `react-native-timer-picker` (base do cronômetro atual), admob.
- App atual já faz sorteio local e timer — o MVP move o sorteio para o backend
  e adiciona auth, rachas, quadras e reservas.

**Stack fixada (constituição)**: React Native (iOS/Android); Spring Boot com
API REST em `/api/**` (JWT stateless); painel admin Thymeleaf + Tailwind CDN
em `/admin/**` (sessão); banco relacional (PostgreSQL em produção).

## Verificação de Constituição

| Princípio | Como o plano atende |
|---|---|
| I. Qualidade > velocidade | Fases incrementais; cada fase entrega fluxo completo e testado |
| II. UX consistente | Telas novas seguem o design system do redesign; componentes compartilhados no app |
| III. Privacidade | DTOs de resposta por contexto (nunca expor entidade); participante vê só nome/nível; agenda do dono vê só nome+contato do organizador |
| IV. Testes de regras críticas | Sorteio, preço e conflito de reserva com testes unitários + integração (Testcontainers) na mesma task da implementação |
| V. Performance | FlashList/FlatList virtualizada, paginação nos endpoints de listagem, optimistic UI |
| VI. Incremental | Fases 0–8 abaixo, cada uma com app compilando e testes verdes |

## Decisões Técnicas (alternativas comparadas)

### D1 — Duas cadeias de autenticação no mesmo backend
| Alternativa | Prós | Contras |
|---|---|---|
| **A. Dois beans `SecurityFilterChain` com `@Order` e `securityMatcher`** ✅ | Padrão idiomático do Spring Security 7; isolamento total (JWT stateless em `/api/**`, form login + sessão em `/admin/**`); `session-jdbc` já no pom | Nenhum relevante |
| B. Uma cadeia única com lógica condicional | — | Mistura sessão e stateless, frágil e difícil de testar |
| C. Dois deployables separados | Isolamento máximo | Contra a decisão do usuário (admin no mesmo backend) |

**Decisão**: A. `@Order(1)` → `securityMatcher("/api/**")`, stateless,
resource server JWT. `@Order(2)` → `/admin/**` + form login, sessão JDBC,
CSRF habilitado. Regra extra: login de `/api/auth/login` recusa token para
DONO_QUADRA com status ≠ APROVADO (FR-003).

### D2 — Biblioteca JWT
**Decisão (ajustada pelo usuário)**: **JJWT 0.12.6**, espelhando o
photographer-manager: `security/JwtService` (segredo Base64 via
`jwt.secret`, HMAC-SHA256, expiração via `jwt.expiration-ms`) +
`security/JwtAuthFilter` (`OncePerRequestFilter`, adicionado antes do
`UsernamePasswordAuthenticationFilter` na cadeia `/api/**`).

### D3 — Refresh token
| Alternativa | Prós | Contras |
|---|---|---|
| **A. Refresh token opaco, rotativo, persistido em tabela** ✅ | Revogável (logout, rejeição de dono), rotação detecta reuso/roubo; access token curto (15min) | Uma tabela e um endpoint a mais |
| B. Access token longo (30d) | Zero infra | Irrevogável; risco alto se vazar |
| C. Refresh token JWT | Sem tabela | Não revogável sem blacklist (vira a A com mais passos) |

**Decisão**: A. `POST /api/auth/refresh` troca o refresh token por um novo par;
reuso de token já rotacionado invalida a família inteira.

### D4 — Armazenamento de fotos (perfil e quadra)
| Alternativa | Prós | Contras |
|---|---|---|
| **A. Interface `StorageService` — disco local em dev, S3-compatible em prod** ✅ | MVP roda sem infra externa; troca para S3/R2/MinIO sem tocar nos serviços; URLs servidas pelo backend com resize futuro | Precisa da abstração desde o início (barato) |
| B. Direto no S3 desde já | Prod-ready imediato | Exige bucket/credenciais para todo dev; atrito no MVP |
| C. BLOB no banco | Backup simples | Incha o banco, pior para servir imagem em mobile |

**Decisão**: A. Implementação `LocalDiskStorage` no MVP + propriedade de
configuração para plugar `S3Storage` depois. Upload multipart com validação de
tipo/tamanho.

### D5 — Notificações (aprovação de dono, cancelamentos, lembretes)
| Alternativa | Prós | Contras |
|---|---|---|
| **A. Central in-app persistida (tabela `notificacao`) + push via Expo Push API** ✅ | O app já é Expo — push sem configurar FCM/APNs na mão; a tabela é fonte de verdade (badge, histórico) mesmo se o push falhar | Dependência do serviço da Expo (aceitável no MVP) |
| B. Só push FCM direto | Sem intermediário | Config nativa iOS+Android; sem histórico in-app; efêmero |
| C. Só polling in-app | Simples | Sem aviso em tempo real (cancelamento de reserva precisa avisar rápido) |

**Decisão**: A. Backend grava `notificacao` e dispara HTTP para
`exp.host/--/api/v2/push/send` com os tokens Expo registrados no login.
Lembrete de racha (dia/hora) via `@Scheduled` no MVP.

### D6 — Pagamento
Fora do MVP (decisão C7). `Reserva.status` não tem estados de pagamento; o
detalhe da reserva exibe o contato da quadra para combinar por fora. O enum de
status fica extensível para um futuro `AGUARDANDO_PAGAMENTO`.

### D7 — Banco em desenvolvimento e testes
**Decisão (ajustada pelo usuário)**: conexão JDBC configurada via **`.env`**,
no mesmo padrão dos outros backends da casa (ex.: ninfin-api/framelio-backend):

```properties
spring.config.import=optional:file:.env[.properties]
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:sortifyteams}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
```

- `.env` na raiz do projeto (gitignorado) aponta para qualquer Postgres — local,
  Docker ou remoto; `.env.example` versionado documenta as variáveis
  (DB_*, JWT_SECRET, STORAGE_PATH, etc.).
- PostgreSQL em todos os ambientes — mesmo dialeto em dev/teste/prod, então a
  constraint única e o lock da reserva (FR-009) se comportam igual.
- **Testcontainers** nos testes de integração (sobe Postgres efêmero, não
  depende do `.env`).
- **Schema gerenciado pelo Hibernate** (`ddl-auto=update`), como no
  photographer-manager — sem Flyway. Seed dos esportes via `data.sql`
  idempotente (`ON CONFLICT DO NOTHING`), com
  `defer-datasource-initialization=true`. Tabelas do Spring Session criadas
  pelo próprio Spring Session (`initialize-schema=always`).

### D8 — Mobile: base técnica das features novas
| Tema | Alternativas | Decisão |
|---|---|---|
| Versão | Manter Expo 49 vs **upgrade para SDK atual** ✅ | Upgrade primeiro (Fase 0): SDK 49 não recebe mais builds EAS e bloqueia `expo-notifications`/`expo-linking` atuais |
| Server state | Redux Toolkit vs **TanStack Query** ✅ | Query: cache/refetch/optimistic prontos, menos boilerplate; estado global mínimo (auth) em Context/Zustand |
| HTTP | fetch manual vs **axios com interceptor** ✅ | Interceptor injeta Bearer e faz refresh automático no 401 (D3) |
| Listas | FlatList vs **FlashList** ✅ | Constituição exige virtualização; FlashList tem melhor perf em listas de quadras/jogadores |
| Deep link (C9) | **expo-linking + universal links** ✅ | `racha://convite/<token>` + fallback web; nativo do Expo |
| Navegação | Manter React Navigation 6 ✅ (upgrade junto do SDK) | Já usada no app; tabs por papel (Jogador vs Dono) em navigators separados |

## Modelo de Dados (PostgreSQL, schema via Hibernate)

Seguindo o padrão do photographer-manager: IDs **ULID** (String de 26 chars,
gerado em `@PrePersist` com `UlidCreator`), tabelas `tb_*`, colunas em
português, relações por coluna de ID (sem associações JPA), anotações
`@Schema` do Swagger nas entidades.

```
usuario           (id, nome_completo, username UQ, email UQ, senha_hash,
                   role, status, foto_path?, contato?,
                   esporte_preferido_override_id? FK esporte)
refresh_token     (id, usuario_id FK, token_hash UQ, familia, expira_em,
                   revogado)
push_token        (id, usuario_id FK, expo_token, plataforma, atualizado_em)
esporte           (id, nome, icone, exige_goleiro,
                   jogadores_minimos_por_time)          -- seed via migração
racha             (id, esporte_id FK, organizador_id FK, data, horario,
                   quadra_id? FK, status, limite_vagas?, token_convite UQ,
                   balancear_nivel, qtd_times, duracao_partida_seg?)
time              (id, racha_id FK, numero)
participante_racha(id, racha_id FK, usuario_id? FK, nome_avulso?,
                   nivel_tecnico 1..5, e_goleiro, time_id? FK)
                   -- CHECK: usuario_id OU nome_avulso
quadra            (id, dono_id FK usuario, nome, endereco, contato, ativa)
quadra_foto       (id, quadra_id FK, path, ordem)
quadra_horario    (id, quadra_id FK, dia_semana, hora_inicio, hora_fim,
                   preco)                                -- grade semanal (C12)
reserva           (id, quadra_id FK, racha_id FK, data, status, preco_total)
reserva_horario   (id, reserva_id FK, quadra_horario_id FK, data,
                   UNIQUE (quadra_horario_id, data))     -- C8/FR-009
notificacao       (id, usuario_id FK, tipo, titulo, corpo, payload_json,
                   lida, criada_em)
```

Pontos de atenção:
- **FR-009**: `UNIQUE (quadra_horario_id, data)` em `tb_reserva_horario`
  (gerada pelo Hibernate via `@UniqueConstraint`). Como não há Flyway para
  criar índice parcial, o cancelamento **remove** as linhas de
  `tb_reserva_horario` (liberando os slots) e preserva o histórico em
  `tb_reserva` (status). Confirmação em transação + tratamento de
  `DataIntegrityViolationException` → HTTP 409 com horários alternativos.
- **Esporte preferido (C11)**: calculado por query (moda de esporte nos rachas
  CONCLUÍDOS do usuário), com override manual na coluna do usuário.
- **Dashboard (C14)**: query agregada `COUNT(racha) GROUP BY quadra` com
  filtro `status = CONCLUIDO`; view SQL para facilitar expansão por horário.

## Contrato de API (REST, `/api/**`)

```
Auth
  POST /api/auth/registro           {papel: JOGADOR|DONO_QUADRA}
  POST /api/auth/login              → {access, refresh} | 403 PENDENTE/REJEITADO
  POST /api/auth/refresh
  POST /api/auth/logout             (revoga refresh)
  POST /api/auth/reenviar-solicitacao   (C13, dono rejeitado)
Perfil
  GET/PUT /api/perfil               PUT /api/perfil/foto (multipart)
  POST /api/perfil/push-token
Esportes
  GET /api/esportes
Rachas
  POST /api/rachas                  GET /api/rachas (do usuário, paginado)
  GET /api/rachas/{id}              DELETE /api/rachas/{id} (cancela, C10)
  POST /api/rachas/{id}/participantes      DELETE .../participantes/{pid}
  GET /api/usuarios/busca?q=        (C3 — convite de cadastrado)
  POST /api/rachas/{id}/sorteio     (FR-007) → times
  POST /api/rachas/{id}/concluir    {duracao_partida_seg?} (C1/C14)
  GET /api/convites/{token}         POST /api/convites/{token}/entrar (C9)
  DELETE /api/rachas/{id}/participantes/me  (sair)
Quadras (público jogador)
  GET /api/quadras?filtros&page     GET /api/quadras/{id}
  GET /api/quadras/{id}/disponibilidade?data=
Reservas
  POST /api/reservas                (FR-008/009) → 201 | 409 + alternativas
  GET /api/reservas/{id}            DELETE /api/reservas/{id}
Dono de Quadra
  GET/POST /api/dono/quadras        GET/PUT/DELETE /api/dono/quadras/{id}
  POST /api/dono/quadras/{id}/fotos (multipart)
  PUT /api/dono/quadras/{id}/horarios   (grade semanal)
  GET /api/dono/agenda?de=&ate=     DELETE /api/dono/reservas/{id} (C10)
Notificações
  GET /api/notificacoes?page        PUT /api/notificacoes/{id}/lida
```

Painel admin (`/admin/**`, Thymeleaf + sessão): `/admin/login`,
`/admin/solicitacoes` (aprovar/rejeitar), `/admin/dashboard` (C14).

## Arquitetura do Backend

Estrutura espelhada do photographer-manager (`com.gomesdev.sortifyteams`):
- `config/` — SecurityConfig (duas chains), PasswordConfig, ErrorDetails
  (RestControllerAdvice com `ErrorResponse {timestamp, message[]}`),
  `config/storage/` (StorageService, LocalStorageService).
- `security/` — JwtService (JJWT) e JwtAuthFilter.
- `domain/<feature>/` — Entity, Controller, Repository, Service +
  subpacotes `request/` e `response/`: `usuario`, `auth` (com `refresh/`),
  `esporte`, `racha` (inclui `sorteio` como serviço puro e testável),
  `quadra`, `reserva`, `notificacao`.
- `admin/` — controllers MVC do painel Thymeleaf.
Camadas: controller → service → repository; DTOs de request/response por
contexto (Princípio III).

O **algoritmo de sorteio** vive em classe pura sem dependência de
Spring/banco (`SorteioService.sortear(List<Jogador>, config)`) — testável por
unidade com seed de aleatoriedade injetável.

## Estratégia de Testes (Princípio IV)

- **Unidade**: sorteio (balanceamento, goleiros, excedente C5, mínimo C6,
  determinismo com seed), cálculo de preço (1 e N horários), transições de
  status de racha/reserva.
- **Integração (Testcontainers/Postgres)**: conflito de reserva concorrente
  (2 threads → 1 sucesso + 1 409), cascata de cancelamento C10, login de dono
  PENDENTE negado, índice único parcial.
- **Web (MockMvc)**: as duas cadeias de segurança (JWT em `/api`, sessão+CSRF
  em `/admin`), contratos de erro.
- **Mobile**: testes de unidade nos hooks de sorteio/preço exibido; fluxo
  crítico manual guiado por checklist até ter Maestro/Detox (fora do MVP).

## Fases de Entrega (entrada do /tasks)

- **Fase 0 — Fundações**: git init nos dois repos; backend: deps espelhando o
  photographer-manager (JJWT, SpringDoc, ULID), `.env` + `.env.example` (D7),
  config das duas security chains, ErrorDetails, JwtService; mobile: upgrade
  Expo SDK, axios+Query, estrutura de pastas. Seed dos esportes entra na
  Fase 2 junto com a entidade Esporte (`data.sql`).
- **Fase 1 — Auth**: registro/login/refresh/logout, papéis e status, telas de
  cadastro/login/aguardando aprovação.
- **Fase 2 — Racha + Sorteio** (núcleo): criar racha, jogadores (avulso +
  busca C3), sorteio com testes, telas do Fluxo 3 + resultado (C4), cronômetro
  vinculado (C1) e concluir racha.
- **Fase 3 — Admin**: painel Thymeleaf (login, aprovações), notificação de
  aprovação/rejeição, reenvio C13.
- **Fase 4 — Quadras (dono)**: CRUD de quadra, fotos (D4), grade semanal.
- **Fase 5 — Reserva**: lista/filtro/detalhe de quadras no app, reserva com
  conflito C8 + testes de concorrência.
- **Fase 6 — Gestão do racha**: convite deep link (C9), sair/cancelar,
  cascatas C10 com notificações (D5), agenda do dono.
- **Fase 7 — Perfil + Dashboard**: perfil completo (C11), dashboard admin
  (C14), lembretes agendados.
- **Fase 8 — Endurecimento**: revisão de privacidade (FR-016), paginação e
  perf das listas, polimento de erros.

## Riscos

1. **Upgrade Expo 49 → atual** é o maior salto (RN novo, libs antigas como
   `react-native-admob` sem manutenção — remover ou trocar). Fazer isolado na
   Fase 0, antes de qualquer feature.
2. **Spring Boot 4.1** é recente — alguma lib de terceiros pode não ter
   catálogo pronto; mitigado usando quase só starters oficiais.
3. **Design system em paralelo** (redesign): telas sem wireframe (resultado do
   sorteio, aguardando aprovação) dependem dele; import do Claude Design ainda
   bloqueado por `/design-login` interativo.
