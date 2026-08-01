# Plano de Implementação: Atualização do app fora da Play Store

**Branch**: `002-atualizacao-in-app` | **Data**: 2026-07-30 | **Spec**: [spec.md](./spec.md)
**Status**: Proposto — aguardando aprovação para `/tasks`

## Contexto Técnico (estado real)

**Backend** — Spring Boot 4.1 / Java 25, PostgreSQL com `ddl-auto=update` +
`data.sql` (sem Flyway), IDs ULID, tabelas `tb_*`, três cadeias de segurança
(`/api/**` JWT, `/admin/**` sessão, catch-all), `StorageService` em disco,
dois `@Scheduled` já em produção (`RachaExpiracaoService`, `LembreteService`),
painel Thymeleaf + Tailwind CDN. APK `1.0.0` versionado dentro de
`src/main/resources/static/downloads/`.

**Mobile** — Expo SDK **49** / RN 0.72, TanStack Query 4, axios com refresh,
`expo-secure-store`, `@stomp/stompjs`. Sem `expo-updates`,
`expo-file-system`, `expo-application`, `react-native-svg`.
`app.json`: `version: 1.0.0`, `appVersionSource: local`, **sem
`android.versionCode`** e **sem `runtimeVersion`**. EAS `preview` gera APK
(distribuição interna), `production` gera app-bundle.

## Verificação de Constituição

| Princípio | Como o plano atende |
|---|---|
| I. Qualidade > velocidade | Fases fecham fluxo completo; a Fase 3 já entrega atualização de APK funcionando antes de o OTA existir |
| II. UX consistente | Tela de atualização desenhada pelo design system, reaproveitando a bola da landing (C18) |
| III. Privacidade | Endpoint de checagem é público e **não recebe nem devolve dado de usuário** — só versão, tamanho, hash e notas |
| IV. Testes de regras críticas | Classificação de versão, `Range`/retomada, expurgo, validações de publicação e teto de tempo têm teste na mesma tarefa |
| V. Performance | Teto de tempo na checagem (C17); download servido em fatias, sem materializar 30 MB; barra com bytes reais |
| VI. Incremental | 7 fases, cada uma com backend compilando e testes verdes; o app ganha valor na Fase 3 sem depender da Fase 5 |

**Ponto de atenção da Constituição VI**: a Fase 5 (OTA) é a única que muda o
comportamento de apps já instalados sem passar por instalador. O rollback
automático (FR-007) é o que a mantém dentro do princípio.

## Decisões Técnicas

### D1 — Onde hospedar a camada de conteúdo (OTA)

| Alternativa | Prós | Contras |
|---|---|---|
| **A. Servidor de updates próprio no Spring** (protocolo `expo-updates`) ✅ | Publicação pelo painel, coerente com FR-017 e C2; mesmo backup e mesma stack; sem limite de usuários ativos; nenhum dado sai de casa | É a maior parte do trabalho desta spec; protocolo errado = app que não atualiza mais |
| B. EAS Update (gerenciado pela Expo) | Liga em horas; rollback e dedup de asset prontos; CDN | Publicação é por CLI, **não pelo painel** — exigiria emendar a FR-017; depende de conta/infra de terceiro e do limite do plano gratuito |
| C. App Center / CodePush | — | Descontinuado pela Microsoft; fora de cogitação |

**Decisão**: **A**. Pesou a direção que o projeto já tomou — APK saindo do
disco para o banco (C22), publicação pelo painel sem redeploy (C2), tudo
servido pelo próprio backend. Colocar justamente a camada mais usada fora de
casa iria contra isso.

Custo assumido, dito com todas as letras: implementar o protocolo é o maior
risco técnico do plano (ver Riscos). **Se a Fase 4 travar**, a saída é adotar
B e emendar a FR-017 para restringir a publicação pelo painel ao runtime — a
spec continua válida, só muda quem aperta o botão do conteúdo.

### D2 — Armazenamento e leitura do binário (C22, FR-010/FR-029)

| Alternativa | Prós | Contras |
|---|---|---|
| **A. `bytea` com `STORAGE EXTERNAL` + `substring()`** ✅ | Leitura por faixa busca só os chunks necessários; apagar é `DELETE`; entra no `pg_dump` normal | Exige `ALTER TABLE` fora do JPA; sem compressão ocupa mais espaço (irrelevante: APK já é zip) |
| B. Large Objects (`lo_*`) | Leitura posicionada nativa, streaming natural | Exige desalocação explícita ao apagar — e o expurgo da C23 é uma rotina recorrente de apagar binário, ou seja, fabricaria órfãos por desenho |
| C. `bytea` padrão (`EXTENDED`) | Zero configuração | Valor comprimido obriga a descompactar tudo para devolver qualquer fatia — mata FR-010 e FR-029 |

**Decisão**: A, com ETag por cima. Como o ajuste de storage é cláusula de
`ALTER TABLE` e o projeto não tem Flyway, ele vai no `data.sql` — que já roda
depois do Hibernate (`defer-datasource-initialization=true`) e já é usado para
seed idempotente:

```sql
ALTER TABLE tb_versao_runtime_arquivo ALTER COLUMN conteudo SET STORAGE EXTERNAL;
```

Só vale para valores novos, então precisa estar em produção **antes** do
primeiro APK gravado.

### D3 — Retomada correta: `Range` + ETag (C10, FR-009/FR-010/FR-011)

Os dois mecanismos se complementam e nenhum substitui o outro: `substring`
resolve *servir um pedaço*, o ETag resolve *garantir que o pedaço é da mesma
versão*.

- Resposta sempre com `Accept-Ranges: bytes` e `ETag: "<sha256>"`.
- Com `Range` válido → `206` + `Content-Range`; sem `Range` → `200` servindo em
  fatias sucessivas (~256 KB) direto no `OutputStream`, para a memória ficar
  plana nos dois caminhos.
- `If-Range` que não bate → `200` com o arquivo novo inteiro, e o cliente
  descarta o parcial sozinho. É a FR-011 resolvida pelo protocolo.
- Redundância proposital no app: ele guarda o ETag com que começou e revalida
  antes de retomar, porque não está confirmado que o
  `createDownloadResumable` envia `If-Range`.
- **`Range` de HTTP é 0-based inclusivo; `substring` de SQL é 1-based**
  (`bytes=0-1023` → `from 1 for 1024`). Erro de 1 byte passa pelo download e só
  estoura na verificação de assinatura do APK — teste dedicado.

### D4 — Integridade verificada no dispositivo (FR-013)

`expo-file-system` calcula **MD5** de arquivo nativamente
(`getInfoAsync({ md5: true })`); calcular SHA-256 de 30 MB em JS custa segundos
e trava a tela.

**Decisão**: publicar **os dois hashes**. SHA-256 é o ETag e o registro
permanente da versão (sobrevive ao expurgo); MD5 é o que o app confere após o
download. MD5 aqui cobre **corrupção de transporte**, não adulteração — a
defesa real contra APK adulterado é a verificação de assinatura do próprio
Android na instalação, que acontece de qualquer forma.

### D5 — `runtimeVersion` e `versionCode` (FR-021, C13)

| Tema | Alternativas | Decisão |
|---|---|---|
| `runtimeVersion` | `policy: appVersion` × **string explícita** ✅ | Explícita (`"1"`), incrementada só quando o nativo muda |
| `versionCode` | `appVersionSource: remote` + autoIncrement × **`local` com número no `app.json`** ✅ | Local e explícito — o release do APK é manual e raro |

**A armadilha que decide D5**: com `policy: appVersion`, todo bump de `version`
(inclusive `1.0.0 → 1.0.1` para uma correção só de JS) muda o runtime e
**invalida a OTA** — exatamente o contrário do que a feature existe para fazer.
Com `runtimeVersion` explícito, `version` sobe à vontade nas entregas de
conteúdo e o runtime só muda quando entra biblioteca nativa.

### D6 — App: download, instalação e tela

| Necessidade | Escolha | Observação |
|---|---|---|
| Download retomável | `expo-file-system` `createDownloadResumable` + `savable()` | O estado de retomada é persistido (SecureStore) para sobreviver ao fechamento do app (C10) |
| Abrir instalador | `expo-intent-launcher` + `FileSystem.getContentUriAsync` | Exige `REQUEST_INSTALL_PACKAGES` no `app.json`; recusa do sistema → fallback no navegador (C5) |
| Versão instalada | `expo-application` (`nativeBuildVersion`) | Mais confiável que `Constants.expoConfig` num APK buildado |
| Bola da tela | `react-native-svg` | Reaproveita os SVGs da landing 1:1; a lib é nativa, mas o APK-ponte da C15 já vai sair mesmo |
| Splash sem salto | `expo-splash-screen` com `preventAutoHideAsync()` | Segura a splash nativa até a tela de atualização estar montada (dependência 9 da spec) |
| Reduzir movimento | `AccessibilityInfo.isReduceMotionEnabled()` | FR-028 |

Rotação da bola: `θ = distância_percorrida / raio`, derivada do progresso real
— rolamento, não giro no lugar (FR-024/FR-025).

### D7 — Agendado do expurgo (C23, FR-030)

| Alternativa | Prós | Contras |
|---|---|---|
| **A. Cron diário que apaga binário fora da política e com mais de 30 dias** ✅ | Previsível; idempotente; mesmo padrão dos dois `@Scheduled` já existentes | Roda todo dia sem fazer nada na maioria das vezes (custo desprezível) |
| B. `fixedDelay` de 30 dias | Parece literal ao pedido | **A contagem reinicia a cada restart** — com deploys frequentes, o job nunca roda |

**Decisão**: A. `app.versao.expurgo-cron` configurável, seguindo
`app.racha.expiracao-cron`. Política: preserva o binário da versão **ativa** e
da **imediatamente anterior**, sempre; as demais perdem o binário depois de 30
dias e viram histórico.

## Modelo de Dados (PostgreSQL, schema via Hibernate)

```
tb_versao_runtime          (id ULID, plataforma, versao, version_code UQ,
                            runtime_version, tamanho_bytes, sha256, md5, notas,
                            version_code_minimo, ativa, publicada_em,
                            publicada_por_id FK usuario, binario_expurgado_em?)
tb_versao_runtime_arquivo  (id, versao_runtime_id UQ FK, conteudo BYTEA
                            [STORAGE EXTERNAL], criado_em)
tb_pacote_conteudo         (id, runtime_version, manifesto_json, notas, ativo,
                            publicado_em, publicado_por_id FK usuario)
tb_asset_conteudo          (id, hash UQ, content_type, conteudo BYTEA, criado_em)
tb_pacote_asset            (id, pacote_id FK, asset_id FK, chave)
```

Pontos de atenção:
- **Metadados e binário separados** (C22): o expurgo apaga
  `tb_versao_runtime_arquivo`; `tb_versao_runtime` é permanente e mantém
  `tamanho_bytes`/`sha256`/`md5` como registro do que a versão era.
- **Asset endereçado por hash** (`tb_asset_conteudo.hash` único): dá dedup
  entre pacotes de conteúdo no servidor e é o que permite ao app baixar só o
  que mudou (FR-004).
- `ativa` como flag exige garantir **uma só ativa por plataforma** e **um só
  pacote ativo por `runtime_version`** — invariante de serviço em transação,
  já que sem Flyway não há índice parcial versionado.

## Contrato de API

```
Público (sem autenticação — liberar /api/app/** na cadeia @Order(1))
  GET /api/app/atualizacao?plataforma=android&runtimeVersion=&versionCode=
        → { runtime: {...} | null, conteudo: {...} | null, minimoSuportado }
  GET /api/app/apk                      versão ativa (Range + ETag + fatias)
  GET /api/app/apk/{versionCode}
  GET /downloads/**                     → 302 para /api/app/apk  (C24)

Protocolo expo-updates (D1-A)
  GET /api/app/manifest                 headers expo-runtime-version, expo-platform
  GET /api/app/assets/{hash}

Admin (/admin/**, sessão + CSRF)
  GET  /admin/versoes                   histórico, quais têm binário
  POST /admin/versoes                   multipart: APK + metadados
  POST /admin/versoes/{id}/ativar       (rollback — só com binário, FR-031)
  POST /admin/versoes/{id}/despublicar
  GET  /admin/conteudo                  pacotes por runtime, qual está ativo
  POST /admin/conteudo                  upload do bundle exportado
  POST /admin/conteudo/{id}/ativar
```

## Arquitetura do Backend

`domain/versaoapp/` seguindo o padrão do projeto (Entity, Repository, Service,
Controller + `request/` e `response/`):
- `VersaoRuntime`, `VersaoRuntimeArquivo`, `PacoteConteudo`, `AssetConteudo`.
- `VersaoAppService` — publicação (transacional: metadados + binário juntos) e
  validações da FR-017.
- `ApkStreamService` — leitura por faixa (`substring`) e escrita em fatias;
  **única** classe que conhece o formato de armazenamento, para trocar sem
  tocar no controller.
- `AtualizacaoService` — monta a resposta da checagem (FR-001/FR-002).
- `UpdatesProtocolController` — protocolo `expo-updates` (D1-A), isolado do
  resto para poder ser substituído pela alternativa B sem arrastar o domínio.
- `VersaoExpurgoService` — `@Scheduled` da C23, no padrão de
  `RachaExpiracaoService`.
- `admin/VersaoAdminController` — telas Thymeleaf.

Upload de ~30 MB: subir `spring.servlet.multipart.max-file-size` e
`max-request-size`, e gravar via stream, sem `MultipartFile.getBytes()`.

## Estratégia de Testes (Constituição IV)

- **Unidade**: classificação em dia/conteúdo/opcional/obrigatória nos limites —
  igual, um abaixo, um acima, runtime divergente (FR-002); política do expurgo
  (FR-030); validações de publicação (FR-017).
- **Integração (Testcontainers)**: `Range` com faixa no meio, faixa aberta,
  faixa inválida e o off-by-one do D3; `If-Range` divergente → 200;
  publicação atômica (falha no meio não deixa metadado nem binário órfão);
  expurgo preservando ativa + anterior e **nunca** apagando registro; download
  concorrente com heap limitado, para provar FR-029.
- **Web (MockMvc)**: `/api/app/**` acessível sem token; rotas de admin
  recusando sem sessão; `/downloads/**` redirecionando (C24).
- **App**: unidade na comparação de versões e na máquina de estados da tela
  (verificando → baixando → pausado → aplicando → erro); retomada e teto de
  tempo verificados em execução real, com rede desligada na mão.
- **Manual obrigatório antes de publicar**: instalar sobre a versão anterior
  com a mesma assinatura (o teste que nenhum automatizado cobre).

## Fases de Entrega (entrada do `/tasks`)

- **Fase 0 — Modelo e publicação (BE)**: entidades, `ALTER TABLE` no
  `data.sql`, publicação pelo painel com validações, limite de upload.
- **Fase 1 — Servir o APK (BE)**: `ApkStreamService` com `Range`/ETag/fatias +
  testes; importar o APK `1.0.0` atual como primeira versão; landing e convite
  dinâmicos; `/downloads/**` redirecionando; **remover o APK do repositório**.
- **Fase 2 — Checagem (BE)**: `GET /api/app/atualizacao`, liberação na cadeia
  `/api/**`, contrato tolerante a cliente antigo (FR-012).
- **Fase 3 — Upgrade do Expo (APP)**: T007 da spec 001 — SDK atual, remover
  `react-native-admob`. Pré-requisito de tudo que vem depois.
- **Fase 4 — Tela e APK no app (APP)**: tela de atualização (logo, barra,
  bola), download retomável, instalador, obrigatória/opcional.
  **Aqui sai o APK-ponte da C15** — distribuído uma última vez pelo site.
- **Fase 5 — Servidor de updates (BE)**: protocolo `expo-updates`, publicação
  de conteúdo pelo painel, assets por hash com dedup.
- **Fase 6 — OTA no app (APP)**: `expo-updates` plugado, conteúdo baixando na
  tela da Fase 4, rollback automático (FR-007).
- **Fase 7 — Expurgo e endurecimento (BE)**: `@Scheduled` da C23, painel
  mostrando quem tem binário, revisão de memória sob download concorrente.

**Marco intermediário**: terminando a Fase 4, o problema original já está
resolvido — usuário atualiza pelo app, sem voltar ao site. As Fases 5–6 trocam
o *tamanho* do download; as 0–4 trocam o *não ter atualização nenhuma*.

## Riscos

1. **Protocolo `expo-updates` implementado errado** (D1-A) — o modo de falha é
   cruel: app que não atualiza mais e só volta com reinstalação manual. Mitiga
   com o `UpdatesProtocolController` isolado, testes contra o cliente real
   antes de publicar, e a rota de fuga para EAS Update (D1-B) sem refazer o
   domínio.
2. **Upgrade do Expo SDK 49** (Fase 3) — mesmo risco já registrado na spec 001;
   é onde o cronograma mais pode escorregar.
3. **`Range` morrendo no proxy reverso** — compressão ou buffering na frente do
   backend derruba a retomada silenciosamente. Verificar em produção com `curl`
   antes de considerar a Fase 1 pronta.
4. **Assinatura divergente** — APK assinado com credencial diferente não
   instala por cima. Prender a credencial ao perfil EAS e testar a instalação
   sobre a versão anterior em toda publicação.
5. **Banco crescendo com binários** — 30 MB por versão retida, refletidos no
   `pg_dump`. A C23 limita, mas o dimensionamento do backup precisa ser
   conferido antes da Fase 1.
6. **OTA ruim derruba todo mundo de uma vez** — sem instalador no caminho, um
   bundle quebrado chega a todos na abertura seguinte. O rollback automático
   (FR-007) é requisito de entrada da Fase 6, não polimento.
