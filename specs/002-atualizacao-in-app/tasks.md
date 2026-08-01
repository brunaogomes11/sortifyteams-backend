# Tarefas: Atualização do app fora da Play Store

**Entrada**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

**Convenções**: `[P]` = pode rodar em paralelo com as vizinhas (arquivos
diferentes, sem dependência). `(BE)` = backend Spring
(`F:\PROJETOS_DEV\Spring\sortifyteams`), `(APP)` = mobile
(`F:\PROJETOS_DEV\React Native\sortify-teams`). Cada tarefa é um incremento
revisável: compila, testes verdes, commit próprio (Constituição VI).
**⚠ = regra crítica**: os testes entram na própria tarefa, não depois
(Constituição IV).

A numeração reinicia a cada feature — referências a tarefas de outra spec vêm
com a spec explícita (ex.: "T007 da spec 001").

## Fase 0 — Modelo e publicação (BE)

- [ ] **T001** (BE) Entidades e repositórios `VersaoRuntime` +
  `VersaoRuntimeArquivo` (`tb_*`, IDs ULID, padrão do projeto); `ALTER TABLE
  tb_versao_runtime_arquivo ALTER COLUMN conteudo SET STORAGE EXTERNAL` no
  `data.sql` (D2 — idempotente, precisa valer **antes** do primeiro APK
  gravado); subir `spring.servlet.multipart.max-file-size` e `max-request-size`
  para caber ~30 MB com folga.
- [ ] **T002** ⚠ (BE) `VersaoAppService.publicar()` transacional — metadados e
  binário na mesma transação, gravando por stream (sem
  `MultipartFile.getBytes()`), calculando sha256 e md5 (D4). Testes na mesma
  tarefa (FR-017): `versionCode` regressivo recusado, arquivo não-APK recusado,
  `minimoSuportado > versionCode` recusado, falha no meio não deixa metadado
  nem binário órfão, invariante de **uma só versão ativa**.
- [ ] **T003** (BE) Painel `/admin/versoes`: lista do histórico, formulário de
  publicação (multipart), ações ativar/despublicar. Teste MockMvc com sessão +
  CSRF, e negando acesso sem sessão de ADMIN.

## Fase 1 — Servir o APK (BE)

- [ ] **T004** ⚠ (BE) `ApkStreamService` (D2/D3): leitura por faixa via
  `substring`, escrita da resposta em fatias (~256 KB) tanto no parcial quanto
  no completo, `Accept-Ranges: bytes`, `ETag: "<sha256>"`, `Content-Range`,
  `If-Range` divergente → `200`. Testes na mesma tarefa: **off-by-one entre
  `Range` 0-based e `substring` 1-based**, faixa no meio, faixa aberta
  (`bytes=N-`), faixa inválida → `416`, `If-Range` que não bate, e download
  concorrente com heap limitado provando FR-029.
- [ ] **T005** (BE) `GET /api/app/apk` e `/api/app/apk/{versionCode}`.
  Inclui **verificação em produção com `curl`** de que o `Range` sobrevive ao
  proxy reverso (risco 3 do plano) — registrar a evidência na tarefa; sem ela a
  fase não fecha.
- [ ] **T006** (BE) Importar o APK `1.0.0` de
  `static/downloads/sortify-teams-v1.0.0.apk` como primeira versão publicada
  (runner idempotente ou ação de painel), com `versionCode` e hashes corretos.
- [ ] **T007** (BE) Landing (`static/index.html`) e página de convite
  (`templates/convite/convite.html`) apontando para o endpoint da versão ativa,
  exibindo versão e data; `/downloads/**` respondendo `302` para
  `/api/app/apk`; **remover o APK de `src/main/resources/static/downloads/`**
  (C24). Depende de T006 — inverter a ordem deixa os links já distribuídos sem
  destino.

## Fase 2 — Checagem (BE)

- [ ] **T008** ⚠ (BE) `AtualizacaoService` + `GET /api/app/atualizacao`
  (FR-001/FR-002): classifica em dia / conteúdo novo / runtime opcional /
  runtime obrigatório. Testes na mesma tarefa nos limites — `versionCode`
  igual, um abaixo, um acima, `runtimeVersion` divergente — e contrato
  tolerante a cliente antigo (FR-012).
- [ ] **T009** [P] (BE) Liberar `/api/app/**` na cadeia `@Order(1)`, com teste
  MockMvc provando acesso **sem token** ao endpoint de checagem e ao download,
  e que nenhuma outra rota de `/api/**` foi aberta junto.

## Fase 3 — Upgrade do Expo (APP)

- [ ] **T010** (APP) Upgrade Expo SDK 49 → atual (React Navigation junto),
  remover `react-native-admob`; app abre e navega em todas as telas existentes.
  É a **T007 da spec 001**, que passa a ser bloqueante aqui.
- [ ] **T011** (APP) Dependências e configuração do release: `expo-updates`,
  `expo-file-system`, `expo-intent-launcher`, `expo-application`,
  `expo-splash-screen`, `react-native-svg`; no `app.json`, declarar
  `android.versionCode` e **`runtimeVersion` explícito** (D5 — nunca
  `policy: appVersion`, que invalidaria a OTA a cada bump de `version`) e a
  permissão `REQUEST_INSTALL_PACKAGES`.

## Fase 4 — Tela e APK no app (APP)

- [ ] **T012** ⚠ (APP) Hook de atualização: consulta a checagem, compara
  versões e expõe a máquina de estados (verificando → baixando → pausado →
  aplicando → erro → obrigatória). Testes de unidade da classificação e das
  transições.
- [ ] **T013** (APP) Tela de atualização (FR-024/FR-026/FR-028): logo, barra de
  progresso e bola em `react-native-svg` com **rotação de rolamento**
  (`θ = distância / raio`, derivada do progresso real); legendas por estado;
  `prefers-reduced-motion` suprimindo a animação sem perder o progresso;
  `preventAutoHideAsync()` costurando a splash nativa sem salto visual.
  Desenhar pelo design system (Constituição II — tela nova sem wireframe).
- [ ] **T014** ⚠ (APP) Download retomável (C10/FR-009/FR-011):
  `createDownloadResumable` com `savable()` persistido em `SecureStore` para
  sobreviver ao fechamento do app; pausa/retoma manual e por queda de rede;
  barra partindo do ponto real de retomada (FR-025); ETag divergente descarta o
  parcial; cancelar apaga o arquivo; espaço em disco insuficiente avisado antes
  de começar. Verificação em execução real com a rede desligada na mão.
- [ ] **T015** (APP) Instalação (FR-013/FR-014): conferir **MD5** do arquivo
  baixado contra o publicado (D4) antes de qualquer coisa; abrir o instalador
  via `getContentUriAsync` + `expo-intent-launcher`; recusa do sistema →
  explicação + fallback no navegador; hash divergente aborta e apaga.
- [ ] **T016** ⚠ [P] (APP) Obrigatória × opcional (FR-015/FR-027): bloqueio sem
  "Depois" abaixo do mínimo suportado; adiar silenciando **por versão**, não
  por sessão; em erro de download, "entrar assim mesmo" disponível **só** acima
  do mínimo. Testes da distinção.
- [ ] **T017** ⚠ [P] (APP) Teto de tempo da checagem (FR-023): estourou, o app
  entra e reagenda; refazer a checagem ao voltar do background respeitando o
  intervalo mínimo (C7). Teste com servidor que não responde.
- [ ] **T018** (APP+BE) **APK-ponte da C15**: build EAS `preview` com a mesma
  credencial de assinatura, publicado pelo painel (T003) e comunicado no site.
  **Teste manual obrigatório**: instalar por cima da `1.0.0` em campo — é o
  único jeito de provar assinatura e `versionCode` (risco 4).

> Fechando a T018, o problema original está resolvido: o usuário atualiza pelo
> app sem voltar ao site. As fases seguintes trocam o **tamanho** do download.

## Fase 5 — Servidor de updates (BE)

- [ ] **T019** (BE) Entidades `PacoteConteudo`, `AssetConteudo` (endereçado por
  `hash` único, dando dedup entre pacotes) e `PacoteAsset`; invariante de **um
  pacote ativo por `runtimeVersion`**.
- [ ] **T020** (BE) `UpdatesProtocolController` (D1-A): `GET /api/app/manifest`
  respeitando os headers `expo-*` e `GET /api/app/assets/{hash}`. Mantido
  **isolado do domínio**, para poder ser trocado por EAS Update sem arrastar o
  resto (rota de fuga do risco 1).
- [ ] **T021** (BE) Painel `/admin/conteudo`: upload do bundle exportado,
  ativar/despublicar, e exibição explícita de qual pacote está ativo em cada
  runtime (FR-005 — publicar no runtime errado não pode passar despercebido).
- [ ] **T022** (BE) Validação do protocolo contra o cliente `expo-updates`
  real, em app instalado, **antes** de qualquer publicação de conteúdo para
  usuários.

## Fase 6 — OTA no app (APP)

- [ ] **T023** (APP) `expo-updates` apontando para o backend, amarrado ao
  `runtimeVersion` da T011.
- [ ] **T024** (APP) Conteúdo baixando dentro da tela da Fase 4: progresso
  agregado, retomada sem rebaixar assets já presentes (FR-004/FR-008),
  aplicação e entrada já atualizado (FR-006).
- [ ] **T025** ⚠ (APP) Rollback automático (FR-007/C14): pacote que falha ao
  iniciar volta sozinho ao anterior (ou ao embutido no APK) e reporta.
  **Requisito de entrada da fase**, não polimento — sem ele um bundle quebrado
  atinge todos os usuários na abertura seguinte (risco 6).

## Fase 7 — Expurgo e endurecimento (BE)

- [ ] **T026** ⚠ (BE) `VersaoExpurgoService` (D7/C23): `@Scheduled` com **cron
  diário** configurável (`app.versao.expurgo-cron`, no padrão de
  `app.racha.expiracao-cron`) — nunca `fixedDelay`, que reinicia a contagem a
  cada restart e nunca dispararia. Apaga binário de versões que não são a
  **ativa** nem a **imediatamente anterior** e passaram de 30 dias. Testes:
  ativa nunca perde binário, anterior preservada, registro de versão jamais
  apagado, expurgo não quebra checagem nem download.
- [ ] **T027** [P] (BE) Painel mostrando quais versões ainda têm binário e
  quais já são só histórico; "voltar atrás" oferecido **apenas** para versão com
  binário (FR-031).
- [ ] **T028** [P] (BE) Dimensionar `pg_dump`/backup com os binários retidos
  (risco 5) e registrar a evidência junto da janela de retenção.
- [ ] **T029** Validação final: executar os 24 critérios de aceitação da spec de
  ponta a ponta e registrar evidências.

## Dependências principais

- **T001 → T002 → T003**: o `ALTER TABLE` da T001 precisa estar em produção
  antes do primeiro binário gravado, senão a leitura por faixa nasce ineficiente
  e exige reescrita da tabela.
- **T006 → T007**: importar antes de remover, ou os links distribuídos quebram.
- **T004/T005 → T014**: sem `Range` comprovado em produção, a retomada do app
  não tem como funcionar.
- **T008 → T012**: o app depende do contrato de checagem.
- **T010 → T011 → T012–T018**: o upgrade do SDK é bloqueante para todo o
  trabalho de app.
- **T018 → T023**: só apps com o APK-ponte instalado recebem OTA (C15).
- **T019–T022 → T023–T025**: o servidor de updates precede o cliente.
- **T026** depende de existir mais de uma versão publicada — na prática, roda
  depois da Fase 4.

## Fora do escopo destas tarefas

Registrado para não virar surpresa em review:
- Limpar os ~60 MB de APK já commitados no histórico do git (`bca1f83`,
  `71a59e6`). A T007 estanca o crescimento; reescrever histórico é decisão à
  parte.
- Cliente iOS (C1) e patch binário de APK (C11).
