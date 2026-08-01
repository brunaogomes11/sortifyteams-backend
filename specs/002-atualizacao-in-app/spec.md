# Especificação de Feature: Atualização do app fora da Play Store

**Branch da feature**: `002-atualizacao-in-app`
**Criada em**: 2026-07-30 | **Revisada em**: 2026-07-30 (sessão 2 — C6 emendada)
**Status**: Clarificada — pronta para `/plan`
**Entrada**: o app Zerinho é distribuído como APK direto
(`https://zerinho.gomesdev.tech`), sem Play Store; hoje não existe caminho de
atualização para quem já instalou.

## Visão Geral

Enquanto o app não estiver na Play Store, quem instalou o APK fica preso na
versão que baixou: não há verificação de versão, aviso de atualização nem
caminho de instalação a partir do app. Na prática, uma correção só chega ao
usuário se ele lembrar de voltar ao site e reinstalar por conta própria — e o
backend evolui junto, então versões antigas quebram em silêncio.

Esta feature entrega o canal de atualização que a Play Store daria, em **duas
camadas**:

| Camada | O que muda | O que o usuário baixa | Como aplica |
|---|---|---|---|
| **Conteúdo (OTA)** — caminho normal | Telas, regras de JS, textos, imagens | Só o bundle JS + os assets que mudaram (ordem de KB a poucos MB) | Sozinho, no reinício do app; sem instalador, sem permissão |
| **Runtime (APK)** — exceção | Biblioteca nativa, versão do Expo, permissão, ícone/pacote | O APK inteiro (~30 MB), com **download retomável** | Instalador do Android, confirmado pelo usuário |

A maior parte das entregas muda só JS e assets — essas passam pela camada de
conteúdo e o usuário nunca vê um download de 30 MB. O APK volta a ser
necessário só quando o **runtime** muda, e aí o download aguenta perder a rede
sem recomeçar do zero.

As duas camadas aparecem para o usuário no mesmo lugar: uma **tela de
atualização na abertura do app**, no modelo de jogo — o app não entra
desatualizado. Ela é a própria splash (logo + barra de progresso com a bola
rolando) e sai sozinha quando não há nada a baixar.

Problema operacional embutido: hoje o APK vive em
`src/main/resources/static/downloads/` — ou seja, **dentro do JAR**. Publicar um
app novo exige rebuild e redeploy do backend. Esta spec desacopla as duas coisas
para as duas camadas.

**Fora do escopo**:
- **iOS** — não há sideload; a camada de conteúdo funcionaria, mas sem um
  cliente iOS distribuído não há o que atualizar. Entra com TestFlight/App Store.
- **Patch binário do APK** (delta tipo bsdiff, como a Play Store faz) —
  avaliado e descartado (C11).
- Publicação na Play Store e seu processo de review (esta feature é a ponte até
  lá).
- Atualização silenciosa do **runtime**: baixar e instalar APK sem
  consentimento. A camada de conteúdo, por natureza, aplica sozinha (C12).

## Clarificações

### Sessão 2026-07-30

- **C1** — Plataforma: **Android apenas**. iOS não permite instalar APK/IPA
  fora da loja; se um cliente iOS existir no futuro, ele ignora a checagem.
- **C2** — Fonte de verdade da versão: **tabela no banco + publicação pelo
  painel admin**. Motivo: publicar app novo não pode depender de
  build/redeploy do backend. ~~O arquivo vai para o storage em disco (volume
  `uploads`, servido em `/files/**`) e o `/downloads/**` estático continua
  como está~~ → **EMENDADA (sessão 4): o APK passa a ser guardado no próprio
  banco (C22) e o download estático sai de cena (C24).**
- **C3** — Comparação de versões de runtime: pelo **`versionCode`** (inteiro
  crescente do Android), que é o que o instalador respeita. O semver (`1.2.0`)
  é só exibição. Publicar `versionCode` menor ou igual ao já publicado é
  recusado.
- **C4** — Atualização **obrigatória** existe: cada versão publicada declara o
  `versionCode` mínimo suportado. Cliente abaixo do mínimo fica com a tela de
  atualização bloqueante (sem "Depois"), porque a API já não garante
  compatibilidade com ele.
- **C5** — Instalação do APK: o app baixa e **abre o instalador do Android**;
  quem confirma é o usuário. O app nunca instala sozinho. Se o sistema recusar
  (permissão de "instalar apps desconhecidos" negada), o fallback é abrir a URL
  de download no navegador.
- **C6** — ~~OTA fica fora do escopo~~ → **EMENDADA (sessão 2): a atualização
  de conteúdo por OTA passa a ser o caminho primário.** A razão original
  (cobre só JS) continua verdadeira, mas é exatamente o que resolve o
  requisito de "não baixar o APK de novo": a esmagadora maioria das entregas
  é JS/asset. O APK deixa de ser o mecanismo padrão e vira a exceção para
  mudança de runtime.
- **C7** — Quando checar: na **abertura do app** e ao **voltar do segundo
  plano**, com intervalo mínimo entre checagens. A checagem acontece **antes do
  login** e não pode depender de token válido. Vale para as duas camadas.
- **C8** — Adiar: numa atualização de runtime opcional, "Depois" silencia o
  aviso por um período; a mesma versão não pode reabrir o modal a cada retorno
  de background. Atualização obrigatória (C4) não é adiável.
- **C9** — Histórico: as versões anteriores continuam disponíveis (permite
  voltar atrás republicando uma anterior), nas duas camadas. ~~Limpeza de
  arquivos antigos é manual — sem expurgo automático nesta feature~~ →
  **EMENDADA (sessão 4): há expurgo automático dos binários antigos (C23); os
  metadados e o histórico permanecem para sempre.**

### Sessão 2026-07-30 (2) — retomada e atualização sem APK

- **C10** — **Download retomável**: um download de APK interrompido (rede caiu,
  app fechado, celular reiniciado) **continua de onde parou**. O progresso
  sobrevive ao fechamento do app; ao voltar, a ação oferecida é "Continuar",
  não "Baixar". Requer que o servidor honre requisições HTTP com `Range` —
  isso vira requisito do lado do backend, não só do app.
- **C11** — **Atualizar sem baixar o APK**: resolvido pela **camada de
  conteúdo (OTA)** — o app busca o bundle JS mais recente e só os assets que
  mudaram em relação ao que já tem. Alternativa descartada: **patch binário do
  APK** (bsdiff/file-by-file, o que a Play Store faz internamente). Motivo da
  recusa: exigiria reconstruir localmente um APK **byte a byte idêntico ao
  assinado** e um módulo nativo de patch que não existe pronto no ecossistema
  Expo — risco e custo altos para ganhar sobre um caminho (OTA) que já elimina
  o download na maioria dos casos.
- **C12** — Consentimento por camada: atualização de **conteúdo** aplica sem
  perguntar, com o progresso visível na tela de abertura (C16) — o usuário não
  escolhe *se* atualiza, ele acompanha. Atualização de **runtime** sempre
  pergunta, porque envolve instalar um pacote (C5).
- **C13** — Compatibilidade entre as camadas: cada APK carrega um identificador
  de **runtime**; um pacote de conteúdo só é aplicado em apps com o runtime
  correspondente. Se a entrega exige runtime novo, o app **não** tenta OTA —
  cai direto no fluxo de APK (Fluxo 5).
- **C14** — Segurança de uma OTA ruim: se o pacote de conteúdo novo falhar ao
  iniciar, o app **volta sozinho** para o último pacote que funcionava (ou para
  o embutido no APK). O admin também pode republicar um pacote anterior como
  atual.
- **C15** — Bootstrap: a camada de conteúdo só existe a partir do primeiro APK
  que já nasce com suporte a ela. A `1.0.0` em campo hoje **não tem** — quem
  está nela precisa de uma instalação de APK (uma última vez, pelo caminho
  atual) para entrar no trilho de atualizações leves.

### Sessão 2026-07-30 (3) — tela de atualização no boot

- **C16** — **A atualização é um gate de abertura, no modelo de jogo**: o app
  verifica e baixa **antes** de deixar entrar, com progresso na tela. Não
  existe app rodando desatualizado enquanto baixa em segundo plano.
- **C17** — **A tela é a splash**, não uma tela a mais: o app abre já nela.
  Estando tudo em dia, ela sai assim que a checagem responde — sem piscar
  "atualizando" para quem não tem o que atualizar. A checagem tem **teto de
  tempo**: estourou, o app entra e checa de novo depois (Constituição V,
  <300ms percebido).
- **C18** — Composição visual: **logo do Zerinho**, **barra de progresso** e
  uma **bola rolando sobre a barra**, avançando com o progresso. A bola é a
  mesma linguagem da landing (`.ball-rotor` em
  [landing.css](../../src/main/resources/static/landing/landing.css)): gira
  continuamente e alterna entre os esportes. Aqui o giro é **de rolamento** —
  a rotação acompanha a distância percorrida na barra, como bola rolando no
  chão, e não um giro solto no lugar.
- **C19** — **Progresso é real**, nunca decorativo: reflete bytes efetivamente
  transferidos. Retomando um download em 43% (C10), a barra abre em 43% — não
  volta a zero para "ficar bonito".
- **C20** — **Falha não prende o usuário**: se a atualização de conteúdo falhar
  (rede, servidor, arquivo inválido), a tela oferece **tentar de novo** e
  **entrar assim mesmo** com a versão que já está instalada. A exceção é o
  cliente abaixo do mínimo suportado (C4), que continua bloqueado — aí a
  incompatibilidade com a API é o motivo, não a falha do download.
- **C21** — Acessibilidade: com "reduzir movimento" ligado no sistema, a bola
  não gira nem alterna esportes; a barra continua indicando o progresso. A
  landing já respeita `prefers-reduced-motion` — a tela mantém a regra.

### Sessão 2026-07-30 (4) — APK no banco e retenção

- **C22** — **O APK inteiro é guardado no banco**, não em arquivo no disco. O
  upload da publicação (Fluxo 7) grava o binário junto dos metadados, na mesma
  transação: ou a versão existe inteira, ou não existe. Some o volume
  `uploads` como dependência do release do app e o backend deixa de ter estado
  em disco para essa feature.
  - **Binário e metadados são registros separados.** O histórico da versão
    (número, notas, quem publicou, quando) é permanente; o binário é o que o
    expurgo (C23) remove. Apagar o arquivo nunca apaga o registro da versão.
  - Contrapartida assumida: a pressão de espaço muda de lugar (volume do
    Postgres e tamanho do backup), não desaparece — quem evita o crash é a
    C23.
- **C23** — **Expurgo automático a cada 30 dias**: um agendado remove os
  binários das versões que não estão em uso, preservando os metadados.
  Mantidos sempre: a **versão ativa** e a **imediatamente anterior**. A
  anterior fica porque é o alvo do "voltar atrás" (C14/Fluxo 7.5) — sem ela, um
  rollback depois de 30 dias exigiria rebuild do APK, e o rollback deixaria de
  ser uma operação de painel.
  - Nenhum expurgo apaga a versão ativa, por definição.
  - O painel mostra quais versões ainda têm binário e quais são só histórico.
- **C24** — **Fim do download estático**: o APK sai de
  `src/main/resources/static/downloads/` (e do JAR). A landing e a página de
  convite passam a apontar para o endpoint que serve a versão ativa a partir
  do banco. O caminho antigo `/downloads/**` continua respondendo, mas
  **redirecionando** para esse endpoint — os links já distribuídos não podem
  quebrar.

## Personas

- **Usuário do app** (Jogador ou Dono de Quadra): recebe conteúdo novo sem
  perceber; quando o runtime muda, decide atualizar e confirma a instalação.
- **Admin**: publica pacote de conteúdo e versão de runtime pelo painel,
  escreve as notas, define o mínimo suportado, despublica e volta atrás.
- **Visitante da landing**: baixa sempre o APK mais recente, sem depender de um
  link com o número da versão no nome.

## Cenários de Usuário

### Fluxo 1 — Tela de atualização na abertura (C16–C21)
1. O app abre direto na tela: **logo**, **barra de progresso** e a **bola
   rolando sobre a barra**, com a legenda do que está acontecendo.
2. Estado **verificando**: a barra roda em indeterminado enquanto o app
   pergunta ao backend o que existe de novo **para o seu runtime**.
3. A resposta leva a um de quatro caminhos: nada a fazer (a tela sai e o app
   entra); conteúdo novo (Fluxo 2, na própria tela); runtime novo opcional
   (Fluxo 5); runtime novo obrigatório (Fluxo 6).
4. Estourando o teto de tempo da checagem (C17), o app entra e checa de novo
   mais tarde — a tela não segura o usuário esperando servidor lento.
5. Sem rede, com erro ou sem nada publicado, **o app segue normalmente** — a
   checagem nunca bloqueia o uso por falha própria (C20).
6. Voltar do background respeitando o intervalo de C7 refaz a checagem; se
   houver conteúdo novo, a tela reaparece antes de devolver o app.

### Fluxo 2 — Atualização de conteúdo (sem APK), na tela de abertura
1. Existe pacote de conteúdo mais novo para o runtime instalado.
2. A tela passa para **baixando**: a barra avança com os bytes reais (C19) e a
   bola rola junto; a legenda mostra o baixado e o total.
3. O app baixa **apenas** o bundle JS e os assets ausentes — o que ele já tem
   em disco não é rebaixado.
4. Download interrompido não perde o que já veio: na retomada só faltam os
   itens que ainda não chegaram, e a barra parte de onde estava.
5. Estado **aplicando** e o app entra já atualizado, com acesso às notas da
   versão (C12).
6. Falhou: a tela mostra o erro com **tentar de novo** e **entrar assim mesmo**
   (C20).
7. Se o pacote novo quebrar na inicialização, o app volta ao anterior sozinho
   e reporta a falha (C14).

### Fluxo 3 — Download de APK retomável (C10)
1. O download do APK usa a **mesma tela de abertura** (C18): barra, bola
   rolando e legenda com baixado / total, além de pausar e cancelar.
2. Perdendo a rede, o download **pausa** em vez de falhar; voltando a rede, ele
   **retoma do ponto em que parou**.
3. Fechando o app no meio (ou reiniciando o celular), o progresso é preservado:
   ao reabrir a tela de atualização, a ação é **Continuar**, com o percentual
   já baixado.
4. Se a versão publicada mudou entre o início e a retomada, o parcial é
   **descartado** e o download recomeça — nunca se costura pedaço de duas
   versões diferentes.
5. Cancelar apaga o arquivo parcial e libera o espaço.
6. Sem espaço em disco suficiente, o app avisa antes de começar.

### Fluxo 4 — Instalação do APK
1. Terminado o download, o app **verifica a integridade** do arquivo contra o
   hash publicado; divergência aborta a instalação, apaga o arquivo e oferece
   baixar de novo.
2. O app abre o instalador do Android com o arquivo verificado (C5).
3. Faltando a permissão de instalar apps de fontes desconhecidas, o app explica
   o que fazer e oferece o fallback pelo navegador.

### Fluxo 5 — Runtime novo, opcional
1. Publicado > instalado, e o instalado ainda está acima do mínimo suportado.
2. A tela de abertura mostra versão, tamanho do download e notas, com as ações
   **Atualizar agora** / **Depois**; "Depois" silencia aquela versão pelo
   intervalo de C8 e entrega o app.
3. "Atualizar agora" leva ao Fluxo 3, na mesma tela.

### Fluxo 6 — Runtime novo, obrigatório
1. O `versionCode` instalado é menor que o mínimo suportado (C4).
2. Mesma tela **sem "Depois"** e sem acesso ao resto do app até atualizar,
   explicando que a versão instalada não é mais compatível.

### Fluxo 7 — Publicar (admin)
1. **Conteúdo**: admin publica um pacote para um runtime, com notas. Ele passa
   a ser o alvo do Fluxo 2 na próxima checagem dos apps daquele runtime.
2. **Runtime**: admin envia o APK e preenche versão (semver), `versionCode`,
   notas e `versionCode` mínimo suportado.
3. O sistema recusa `versionCode` menor ou igual ao publicado (C3), arquivo que
   não seja APK e mínimo suportado maior que a própria versão.
4. Publicada, vale imediatamente — **sem redeploy do backend** (C2).
5. Admin pode despublicar ou reativar uma versão/pacote anterior para voltar
   atrás (C9/C14) — desde que ela ainda tenha binário (C23).
6. O painel deixa explícito qual pacote de conteúdo está ativo para cada
   runtime — publicar conteúdo no runtime errado não pode passar despercebido —
   e quais versões já são apenas histórico, sem binário.

### Fluxo 9 — Expurgo dos binários antigos (C23)
1. A cada 30 dias, um agendado varre as versões de runtime publicadas.
2. Mantém o binário da **versão ativa** e da **imediatamente anterior**;
   remove os das demais.
3. Os metadados (versão, `versionCode`, notas, hash, tamanho, quem publicou)
   **permanecem** — a versão vira histórico, não some.
4. O painel passa a mostrar essas versões como "sem binário", e a ação de
   voltar atrás fica indisponível para elas.
5. Nada em uso é afetado: a checagem e o download da versão ativa continuam
   funcionando durante e depois do expurgo.

### Fluxo 8 — Landing sempre atual
- O botão "Baixar o app" aponta para a versão de runtime publicada mais
  recente, sem o número fixado no HTML, e mostra versão e data de publicação.

## Requisitos Funcionais

### Checagem e decisão

- **FR-001**: O backend expõe, **sem autenticação**, o que há de novo para um
  runtime informado pelo app: pacote de conteúdo atual (se houver) e versão de
  runtime publicada mais recente (versão, `versionCode`, URL, tamanho, hash,
  notas, mínimo suportado, data). Nenhum dado de usuário trafega nesse
  contrato.
- **FR-002**: O app classifica o resultado em: em dia, conteúdo novo, runtime
  opcional ou runtime obrigatório, comparando runtime por identificador (C13)
  e versão por `versionCode` (C3). **Regra crítica — testes obrigatórios** nos
  limites (igual, um abaixo, um acima, runtime divergente).
- **FR-003**: A checagem roda na abertura e ao voltar do background, com
  intervalo mínimo, sem exigir token e falhando em silêncio (C7).
- **FR-012**: O contrato de checagem é tolerante a cliente antigo: campo novo
  não pode quebrar app já instalado — é justamente ele que precisa conseguir
  atualizar.

### Tela de atualização (C16–C21)

- **FR-022**: O app abre na tela de atualização e **só entrega o app depois**
  de resolver o que há para atualizar (C16). Não há uso do app com download de
  conteúdo correndo por baixo.
- **FR-023**: A tela é a splash: em dia, ela sai assim que a checagem responde,
  sem exibir estado de atualização; a checagem tem teto de tempo, após o qual o
  app entra e reagenda (C17). **Regra crítica — testes obrigatórios** do teto
  de tempo, para não trocar "app trava sem rede" por "app trava com servidor
  lento".
- **FR-024**: A tela compõe **logo**, **barra de progresso** e **bola rolando
  sobre a barra**, com a rotação da bola proporcional ao avanço (rolamento, não
  giro no lugar), reaproveitando a linguagem visual da bola da landing (C18) e
  o design system do app (Constituição II).
- **FR-025**: A barra reflete **bytes reais** transferidos, incluindo o ponto
  de retomada de um download parcial (C19). Progresso simulado é proibido.
- **FR-026**: A tela cobre os estados: verificando, baixando (com baixado /
  total), pausado, aplicando, erro e atualização obrigatória — cada um com
  legenda que diz o que está acontecendo.
- **FR-027**: Em erro de atualização de conteúdo, a tela oferece **tentar de
  novo** e **entrar assim mesmo**; cliente abaixo do mínimo suportado não
  recebe a segunda opção (C20). **Regra crítica — testes obrigatórios** dessa
  distinção.
- **FR-028**: Com "reduzir movimento" ativo no sistema, a animação da bola é
  suprimida sem perder a indicação de progresso (C21).

### Camada de conteúdo (C11)

- **FR-004**: Uma entrega que muda apenas JS e assets é distribuída sem novo
  APK: o app baixa o bundle e **somente os assets que ainda não possui**.
- **FR-005**: Um pacote de conteúdo só é aplicado em app com o runtime
  correspondente (C13); havendo runtime novo, o app segue para o fluxo de APK.
- **FR-006**: Conteúdo é aplicado no reinício, com aviso discreto e notas
  acessíveis (C12).
- **FR-007**: Pacote de conteúdo que falha ao iniciar provoca **retorno
  automático** ao último pacote funcional (ou ao embutido no APK), e o admin
  pode republicar um pacote anterior (C14). **Regra crítica — testes
  obrigatórios** do caminho de volta.
- **FR-008**: Download de conteúdo interrompido não recomeça do zero: na
  retomada, apenas os itens faltantes são buscados.

### Camada de runtime (APK)

- **FR-009**: O download do APK é **retomável** — pausa/retoma manual e
  automática por queda de rede, com progresso persistido entre execuções do
  app (C10). **Regra crítica — testes obrigatórios**.
- **FR-010**: O backend serve o APK honrando requisições **HTTP `Range`**
  (resposta parcial), pré-condição da retomada (C10). **Regra crítica —
  testes obrigatórios**, inclusive atrás do proxy reverso de produção.
- **FR-029**: Servir o APK **não carrega o arquivo inteiro em memória** a cada
  requisição, nem no download completo nem no parcial (C22). Com dezenas de MB
  por versão e downloads concorrentes, materializar o binário por requisição
  derruba o backend. **Regra crítica — testes obrigatórios** de download
  concorrente.
- **FR-011**: Parcial de uma versão que deixou de ser a publicada é descartado;
  bytes de versões diferentes nunca são unidos. **Regra crítica — testes
  obrigatórios.**
- **FR-013**: Antes de instalar, o arquivo baixado é verificado contra o hash
  publicado; divergência aborta, apaga o parcial e oferece nova tentativa.
- **FR-014**: O app dispara o instalador do sistema, sempre com confirmação do
  usuário, e cai para o navegador se o sistema recusar (C5).
- **FR-015**: Atualização obrigatória bloqueia o uso do app até a instalação
  (C4); a opcional é adiável por versão, não por sessão (C8).
- **FR-016**: Cancelar o download apaga o arquivo parcial; falta de espaço é
  avisada antes de começar.

### Publicação e operação

- **FR-017**: Admin publica conteúdo e runtime pelo painel, sem redeploy (C2).
  **Regra crítica — testes obrigatórios** das validações de publicação
  (`versionCode` não regressivo, tipo de arquivo, mínimo coerente, runtime
  alvo explícito).
- **FR-018**: Só ADMIN autenticado publica, despublica ou reativa; a cadeia
  `/admin/**` já existente é a que protege essas rotas.
- **FR-019**: O APK é **gravado no banco** junto dos metadados, na mesma
  transação, e servido por URL pública estável (C22). Fora do JAR e fora do
  disco. Onde ficam os artefatos da camada de conteúdo depende de onde ela for
  hospedada — decisão do plano.
- **FR-020**: A landing usa a versão de runtime publicada mais recente e exibe
  versão e data (Fluxo 8).
- **FR-021**: O processo de release garante `versionCode` incrementado e
  **assinatura igual** à do APK instalado — sem isso o Android recusa a
  atualização.
- **FR-030**: Um agendado a cada 30 dias remove os binários das versões que não
  são a **ativa** nem a **imediatamente anterior**, preservando os metadados e o
  histórico (C23). **Regra crítica — testes obrigatórios**: o expurgo nunca
  pode remover o binário da versão ativa nem apagar registro de versão.
- **FR-031**: O painel mostra, por versão, se o binário ainda existe ou se ela
  já é apenas histórico — e só oferece "voltar atrás" para versão que ainda tem
  binário (C23).
- **FR-032**: A landing e a página de convite apontam para o endpoint da versão
  ativa; o caminho legado `/downloads/**` **redireciona** para ele em vez de
  servir arquivo estático, e o APK sai do repositório (C24).

## Entidades-Chave (entrada do `/plan`)

- **VersaoRuntime** (metadados — **permanentes**): id, plataforma (`ANDROID`),
  versao (semver, exibição), version_code (int, comparação), runtime_id (amarra
  a camada de conteúdo), tamanho_bytes, hash_arquivo, notas,
  version_code_minimo_suportado, publicada (bool), publicada_em, publicada_por,
  binario_expurgado_em?.
  - Invariantes: `version_code` único por plataforma; ao publicar,
    `version_code` > maior publicado; `minimo_suportado` ≤ `version_code`.
  - `tamanho_bytes` e `hash_arquivo` continuam preenchidos depois do expurgo —
    são o registro do que aquela versão era.
- **ArquivoVersaoRuntime** (binário — **expurgável**): id, versao_runtime_id,
  conteúdo do APK, criado_em.
  - Invariantes: no máximo um por versão; a versão **ativa** sempre tem o seu
    (C23); removê-lo não remove a `VersaoRuntime` correspondente.
  - Precisa permitir leitura por faixa de bytes sem materializar o binário
    inteiro (FR-029/FR-010).
- **PacoteConteudo**: id, runtime_id, identificador do pacote, notas, ativo
  (bool), publicado_em, publicado_por, referência aos artefatos (bundle +
  assets) no storage.
  - Invariantes: no máximo um pacote **ativo** por `runtime_id`; publicar um
    novo desativa o anterior sem apagá-lo (C9/C14).

## Critérios de Aceitação de Alto Nível

1. **Entrega só de JS**: com o app instalado e uma correção de tela publicada
   como conteúdo, o usuário reabre o app e está na versão nova — **sem
   download de APK e sem passar pelo instalador**.
2. **Conteúdo com asset novo**: publicando um pacote que muda uma imagem, o app
   baixa a imagem nova e o bundle, e não rebaixa os assets que já tinha.
3. **Runtime novo**: com `1.0.0` (`versionCode` 1) instalada e `1.1.0`
   (`versionCode` 2) publicada, o app avisa, baixa com progresso e abre o
   instalador; depois de instalar, a próxima abertura não avisa mais nada.
4. **Retomada por queda de rede**: desligando a rede com o download do APK pela
   metade, ele pausa; religando, ele **continua do mesmo ponto** — o total
   trafegado é próximo do tamanho do APK, não do dobro.
5. **Retomada após fechar o app**: matando o app com o download pela metade e
   reabrindo, a tela mostra o percentual anterior e a ação **Continuar**.
6. **Versão trocada no meio**: publicando uma versão nova enquanto existe um
   parcial da anterior, o parcial é descartado e o download recomeça na versão
   certa.
7. **Obrigatória**: publicando `1.2.0` com mínimo `2`, um app em `versionCode` 1
   fica na tela bloqueante sem "Depois"; um app em `versionCode` 2 recebe aviso
   opcional e consegue adiar.
8. **Publicação sem deploy**: admin publica pelo painel e o app de um usuário vê
   a novidade **sem o backend ser reconstruído ou reiniciado**.
9. **Validação de publicação**: `versionCode` igual ou menor que o atual é
   recusado com erro claro, e a versão publicada anterior continua intacta.
10. **OTA ruim**: publicando um pacote de conteúdo que quebra na inicialização,
    o app volta sozinho ao anterior e continua utilizável.
11. **Offline**: sem rede na abertura o app entra normalmente; ao recuperar a
    rede e voltar do background, o aviso aparece.
12. **Integridade**: arquivo com hash divergente não chega ao instalador — o app
    avisa e permite tentar de novo.
13. **Landing**: o botão baixa a versão mais recente publicada, sem editar HTML a
    cada release.
14. **Tela em dia**: sem nada publicado, abrir o app **não** exibe estado de
    atualização — a splash sai e o app entra, sem piscar barra de progresso.
15. **Tela atualizando**: com conteúdo novo publicado, abrir o app mostra logo,
    barra avançando e a bola rolando sobre ela até o fim, e então entra já na
    versão nova.
16. **Servidor lento**: com a checagem sem resposta, o app entra depois do teto
    de tempo em vez de ficar preso na tela.
17. **Erro com escape**: falhando o download de conteúdo num cliente acima do
    mínimo suportado, a tela oferece entrar assim mesmo — e entra. No cliente
    abaixo do mínimo, essa saída não existe.
18. **Reduzir movimento**: com a preferência ligada no sistema, a bola não anima
    e o progresso continua legível.
19. **Publicação atômica**: falhando o envio do APK no meio, nenhuma versão
    parcial fica publicada — nem metadados órfãos, nem binário órfão.
20. **Download do banco com retomada**: baixar o APK servido a partir do banco
    aceita `Range` e retoma do ponto exato, igual ao critério 4.
21. **Expurgo**: rodando o agendado com cinco versões publicadas ao longo do
    tempo, sobram os binários da ativa e da anterior; as demais viram só
    histórico no painel, com versão, notas e data intactos.
22. **Expurgo não quebra o app**: depois de rodar, a checagem e o download da
    versão ativa continuam funcionando normalmente.
23. **Rollback**: o admin volta para a versão anterior pelo painel mesmo depois
    do expurgo ter rodado.
24. **Link legado**: a URL antiga `/downloads/sortify-teams-v1.0.0.apk`
    redireciona para o APK da versão ativa em vez de dar 404 ou entregar um
    arquivo velho.

## Revisão & Aceite

- [x] `[PRECISA CLARIFICAR]` resolvidos (C1–C24; C6 emendada na sessão 2, C12
      na sessão 3, C2 e C9 na sessão 4)
- [x] Requisitos testáveis e sem ambiguidade
- [x] Escopo delimitado (sem iOS, sem patch binário, sem Play Store)
- [ ] Plano técnico aprovado (`/plan`)

## Dependências e observações para o plano

1. **Onde hospedar a camada de conteúdo** é a decisão de maior impacto do
   plano: serviço gerenciado da Expo (mais rápido de ligar, depende de infra e
   conta de terceiro) × servidor de updates próprio no backend Spring (tudo em
   casa, mas exige implementar o protocolo de updates corretamente). Ambas
   atendem esta spec — a escolha muda a fase de tarefas, não os requisitos.
2. **Upgrade do Expo SDK 49** (T007 da spec 001) é pré-requisito prático:
   a camada de conteúdo é módulo nativo e o SDK 49 é de 2023.
3. **`app.json` sem `android.versionCode`** e com `appVersionSource: local` — o
   plano precisa definir onde esse número vive e como entra no release (FR-021).
4. **Assinatura**: o APK de atualização precisa da mesma credencial do
   instalado; o perfil EAS `preview` gera APK, o `production` gera app-bundle.
5. **`Range` no `/files/**`**: o Spring MVC responde parcial para handler que
   devolve `Resource`, mas isso precisa ser **verificado de ponta a ponta em
   produção** — proxy reverso com compressão ou buffering pode remover o
   suporte e matar a retomada (FR-010).
6. **Limite de upload**: `spring.servlet.multipart.max-file-size` está em 10 MB
   e o APK tem ~30 MB. Com o binário indo para o banco (C22), o caminho
   upload → banco também não pode passar pela memória inteira de uma vez.
7. **Rota de checagem** precisa ser liberada na cadeia `/api/**`, que hoje só
   libera `/api/auth/**`.
8. **Bootstrap (C15)**: quem está na `1.0.0` atual só entra no trilho leve
   depois de instalar uma vez o APK que já traz a camada de conteúdo — vale
   planejar essa comunicação.
9. **Tela x splash nativa**: a splash declarada no `app.json` (`splash.image`)
   aparece antes de qualquer código do app rodar. O plano precisa costurar as
   duas para não haver salto visual entre a splash nativa e a tela de
   atualização — inclusive segurando a splash nativa até a tela estar pronta.
10. **A bola é SVG na landing** e alterna 4 esportes por crossfade; o plano
    define como isso chega ao React Native (SVG vs sprite/imagem) e de onde
    sai o giro proporcional ao progresso (FR-024). É tela nova sem wireframe —
    mesma situação de C4 na spec 001: desenhar pelo design system.
11. **Como guardar e servir o binário no Postgres** determina se FR-010 e
    FR-029 são atendíveis. Encaminhamento (decisão formal e alternativas vão
    no `plan.md`): **coluna binária com armazenamento externo sem compressão +
    leitura por faixa (`substring`)**, com **ETag = sha256 + `If-Range`** por
    cima. Os dois se complementam — a leitura por faixa resolve *servir um
    pedaço*, o ETag resolve *garantir que o pedaço é da mesma versão* (FR-011),
    e nenhum dos dois substitui o outro. Pontos de atenção herdados:
    - Sem compressão é pré-requisito, não detalhe: APK já é zip, e valor
      comprimido obriga a descompactar tudo para devolver qualquer fatia.
    - O ajuste de armazenamento é cláusula de `ALTER TABLE`, não expressável
      em anotação JPA; sem Flyway no projeto, o lugar natural é o `data.sql`
      (já idempotente e já executado após o Hibernate). Precisa valer **antes**
      do primeiro APK gravado — só afeta valores novos.
    - **Objetos grandes do Postgres foram descartados**: a leitura posicionada
      nativa é atraente, mas exigem desalocação explícita ao apagar, e o
      expurgo da C23 é justamente uma rotina recorrente de apagar binário —
      seria fabricar órfãos por desenho.
    - O download **sem** `Range` também não pode materializar o binário: serve
      em fatias sucessivas direto na resposta.
    - `Range` de HTTP é 0-based inclusivo; `substring` de SQL é 1-based. O
      deslocamento de 1 byte passa pelo download e só estoura na verificação
      de assinatura do APK — sintoma difícil de diagnosticar, então merece
      teste próprio.
12. **Backup e replicação**: cada versão retida engorda o `pg_dump` em ~30 MB.
    Dimensionar junto da janela de retenção (C23) e conferir se a rotina de
    backup atual aguenta.
13. **Migração (C24)**: o APK `1.0.0` que hoje está no repositório precisa ser
    importado como primeira versão publicada **antes** de o arquivo estático
    sair, senão os links já distribuídos ficam sem destino.
14. **Peso no repositório**: o APK já foi commitado duas vezes
    (`bca1f83`, `71a59e6`), ou seja, ~60 MB vivem no histórico do git. Tirar o
    arquivo do repo (C24) estanca o crescimento; limpar o que já está no
    histórico é decisão à parte, fora desta spec.
