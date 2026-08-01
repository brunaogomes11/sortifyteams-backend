# Baseline — Inventário do que já existe

**Levantado em**: 2026-07-30 | **Commit de referência**: `71a59e6`
**Objetivo**: fotografar o estado real dos dois repositórios antes de adotar
SDD para toda feature nova. Serve de linha de base para as specs seguintes e
registra o que foi entregue **sem** spec.

---

## 1. Produto em produção

| Item | Estado |
|---|---|
| Marca | **Zerinho** (nome do app; `slug`/pacote ainda `sortify-teams` / `tech.gomesdev.sortifyteams`) |
| Backend | `https://zerinho.gomesdev.tech` — Docker (`Dockerfile` + `docker-compose.yml`), porta 8083, volume `uploads` |
| App Android | APK `1.0.0` servido em `/downloads/sortify-teams-v1.0.0.apk` (dentro do JAR) |
| App iOS | Não distribuído |
| Play Store | **Não publicado** — origem da spec [002](../002-atualizacao-in-app/spec.md) |
| Landing | `static/index.html` + `static/landing/` com botão de download do APK |
| Painel admin | `/admin/**` (Thymeleaf + Tailwind CDN, sessão JDBC) |
| Swagger | `/swagger-ui.html` |

## 2. Backend — stack e infraestrutura

Spring Boot 4.1 / Java 25, espelhando o photographer-manager: JJWT 0.12.6,
SpringDoc, ULID, `ddl-auto=update` + `data.sql` (sem Flyway), config por `.env`,
tabelas `tb_*`, colunas em português, sem associações JPA (referência por ID).

- **Segurança** — 3 cadeias em [SecurityConfig.java](../../src/main/java/com/gomesdev/sortifyteams/config/SecurityConfig.java):
  `@Order(1)` `/api/**` stateless JWT (só `/api/auth/**` liberado),
  `@Order(2)` `/admin/**` form login + sessão, `@Order(3)` catch-all que libera
  `/`, `/landing/**`, `/downloads/**`, `/files/**`, `/convite/**`, `/ws/**`,
  Swagger e `/error` — o resto é `denyAll`.
- **Transversais** — `ErrorDetails` (`RestControllerAdvice`), `SuccessDetails`,
  `LoggingInterceptor`, `WebConfig`, `AdminSeeder`, `PasswordConfig`.
- **Storage** — `StorageService`/`LocalStorageService` gravam em `uploads/`
  (`app.storage.local-path`), servidos em `/files/**` por `StorageController`.
  `BaseUrlResolver` monta a URL pública (`APP_BASE_URL` ou a requisição).
- **Geo** — `GeocodingService` (Nominatim/OSM, desligável por
  `GEOCODING_ENABLED`) + `GeocodeResult`.
- **Tempo real** — `WebSocketConfig` (STOMP em `/ws`, broker `/topic`) +
  `StompAuthChannelInterceptor` (JWT no frame CONNECT).
- **Agendados** — `RachaExpiracaoService` (cron 03:15, racha ABERTO vencido →
  CANCELADO) e `LembreteService` (cron 08:00, lembrete do dia).

### Módulos de domínio

| Módulo | Endpoints | Origem |
|---|---|---|
| `auth` (+`refresh`) | `POST /api/auth/{registro,login,refresh,logout,reenviar-solicitacao}` | 001 |
| `usuario` | `GET/PUT /api/perfil`, `PUT /api/perfil/foto`, `GET /api/usuarios/busca` | 001 |
| `esporte` | `GET /api/esportes` (seed de 10 esportes em `data.sql`) | 001 |
| `racha` | `POST/GET /api/rachas`, `GET/DELETE /api/rachas/{id}`, participantes (add/remover/sair), `POST .../sorteio`, `POST .../concluir` | 001 |
| `racha` (extras) | `GET /api/rachas/publicos`, `GET /api/rachas/publicos/cidades`, `PATCH /api/rachas/{id}/config`, `PATCH /api/rachas/{id}/times`, `POST /api/rachas/{id}/iniciar` | **sem spec** |
| `racha/sorteio` | `SorteioService` puro + testes | 001 |
| `racha/partida` | `GET /api/rachas/{id}/ao-vivo`, `POST .../partidas`, `POST .../partidas/{pid}/encerrar`, `POST .../partidas/{pid}/gols`, `DELETE .../gols/{golId}` + tópico STOMP `/topic/rachas/{id}` | **sem spec** |
| `racha` (convite) | `GET /api/convites/{token}`, `POST /api/convites/{token}/entrar`, página web `GET /convite/{token}` | 001 (página web **sem spec**) |
| `quadra` | `/api/dono/quadras` (CRUD, fotos, `PUT .../horarios`), `GET /api/quadras`, `GET /api/quadras/{id}`, `GET /api/quadras/{id}/disponibilidade` | 001 |
| `reserva` | `POST /api/reservas`, `GET/DELETE /api/reservas/{id}`, `GET /api/dono/agenda`, `DELETE /api/dono/reservas/{id}`, `POST /api/dono/reservas/{id}/aceitar` | 001 (aceite **sem spec**) |
| `notificacao` (+`push`) | `GET /api/notificacoes`, `PUT /api/notificacoes/{id}/lida`, `POST /api/notificacoes/push-token`, `ExpoPushClient` | 001 |
| `admin` | `/admin/login`, `/admin/solicitacoes` (+aprovar/rejeitar), `/admin/dashboard` | 001 |

### Testes existentes

`SorteioServiceTest` (unidade) e 12 classes de fluxo sobre `IntegrationTestBase`
(Testcontainers): auth, security chains, admin, quadra, racha, racha público,
convite/cascata, partida, WebSocket ao vivo, reserva, perfil/dashboard.

## 3. Mobile — estado

`F:\PROJETOS_DEV\React Native\sortify-teams` — Expo **SDK 49** / RN 0.72,
React Navigation 6, TanStack Query 4, axios com interceptor de refresh,
`expo-secure-store`, `@stomp/stompjs`, styled-components, TS estrito.
22 telas em `app/screens/` (auth, home, racha incl. `aoVivo`, quadras, dono,
perfil, notificações), `app/api/{client,endpoints,ws}.ts`, `app/hooks/`,
`app/lib/`, `app/theme/`.

Build: EAS, perfil `preview` (APK, distribuição interna) e `production`
(app-bundle, `autoIncrement`), ambos apontando para `https://zerinho.gomesdev.tech`.
`app.json`: `version: 1.0.0`, `appVersionSource: local`, **sem `versionCode`
declarado**.

## 4. Cobertura da spec 001 — pendências

| Tarefa | Situação |
|---|---|
| **T007** (APP) upgrade Expo 49 → atual, remover `react-native-admob` | **Aberta** — `admob` ainda em `package.json`; SDK 49 sem builds EAS a médio prazo |
| **T020** (APP) cronômetro vinculado ao racha | Superada na prática pelo fluxo **ao vivo** (partidas/gols) — precisa decisão: fechar como entregue ou re-specificar |
| **T041** (BE) auditoria de privacidade (FR-016) | **Aberta** |
| **T042** (APP) passada de performance | **Aberta** |
| **T043** validação final dos 5 critérios de aceitação | **Aberta** |

## 5. Entregue sem spec (candidatos a retro-spec)

Tudo abaixo entrou no commit `ba5e2b6` ("uma cacetada de mudança") ou depois,
sem passar por `spec.md`. Não há dívida de código — há dívida de documentação.

| # sugerido | Feature | Escopo real no código |
|---|---|---|
| 003 | **Racha ao vivo (partidas e gols)** | `PartidaRacha`, `GolPartida`, dinâmica "vencedor fica", `CriterioEmpateEnum` (5 critérios, um para 0x0 e outro para empate com gols), sugestão da próxima partida por fila de entrantes, snapshot `RachaAoVivoResponse` compartilhado entre REST e STOMP, status `EM_ANDAMENTO` + `POST /iniciar` |
| 004 | **Rachas públicos e descoberta por localização** | `Racha.publico/latitude/longitude/cidade/local`, geocodificação Nominatim, listagem por GPS (Haversine + `raioKm`) ou por cidade, `RachaLocalizacaoService` |
| 005 | **Reserva com aceite do dono** | `StatusReservaEnum.PENDENTE` + `POST /api/dono/reservas/{id}/aceitar` — a 001 previa reserva confirmada direto |
| 006 | **Convite web + landing Zerinho** | Página Thymeleaf `/convite/{token}`, landing pública, distribuição do APK, rebranding |
| 007 | **Expiração automática de rachas** | `RachaExpiracaoService` (cron diário) |
| — | Ajustes de sorteio | `usaNivelTecnico`, `incluirGoleirosNoSorteio`, grupo de goleiros nº 0, edição manual de times (`PATCH /times`) |

## 6. Divergências entre os documentos e o código

Registradas para não induzir a erro quem ler a 001:

1. **Push token** — a 001 previa `POST /api/perfil/push-token`; o código expõe
   `POST /api/notificacoes/push-token`.
2. **Reserva** — a 001 não previa o estado `PENDENTE` nem o aceite do dono.
3. **Cronômetro (C1)** — virou o fluxo ao vivo com partidas e placar, bem além
   de "registrar a duração da partida".
4. **Cadeia catch-all** — a 001/plan não previa rotas públicas fora de
   `/api` e `/admin`; hoje existem landing, downloads, convite web e `/ws`.
5. **Deep link** — além de `racha://convite/<token>`, há fallback web em
   `https://zerinho.gomesdev.tech/convite/<token>`.

## 7. Riscos abertos

1. **Distribuição do app** — sem Play Store e com o APK embutido no JAR, cada
   release do app exige rebuild + redeploy do backend, e o usuário só atualiza
   se voltar ao site por conta própria. Tratado pela spec
   [002](../002-atualizacao-in-app/spec.md).
2. **Expo SDK 49** (2023) — bloqueia libs atuais e, no limite, o próprio build
   EAS. É pré-requisito de qualquer feature mobile que precise de lib nova.
3. **`ddl-auto=update`** — sem Flyway, mudanças destrutivas de schema não são
   versionadas nem reversíveis; combinar com cuidado em produção.
4. **Nominatim** — serviço gratuito com política de ~1 req/s; sem cache local
   dos resultados de geocodificação.
