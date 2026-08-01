# Specs — Desenvolvimento Orientado por Especificação (SDD)

Toda feature nova nasce aqui antes de virar código (Constituição,
["Fluxo de Desenvolvimento"](../.specify/memory/constitution.md)).

## Convenções

- Uma pasta por feature: `specs/NNN-slug-curto/`, numeração sequencial.
- Três arquivos por feature, nessa ordem:
  1. `spec.md` — **o quê** e **por quê**. Personas, fluxos, requisitos
     funcionais (`FR-xxx`), entidades, critérios de aceitação. Perguntas em
     aberto ficam como `[PRECISA CLARIFICAR]` e são resolvidas numa seção de
     **Clarificações** (`Cx`) antes do plano.
  2. `plan.md` — **como**. Decisões técnicas (`Dx`) com alternativas
     comparadas e justificativa, modelo de dados, contrato de API, estratégia
     de testes, fases.
  3. `tasks.md` — **em que ordem**. Tarefas incrementais (`Txxx`), marcadas
     `(BE)` backend / `(APP)` mobile, `[P]` = paralelizável. Regra de negócio
     crítica leva os testes na própria tarefa (Constituição IV).
- A spec só avança para o plano com todos os `[PRECISA CLARIFICAR]` resolvidos.
- Mudou o escopo depois de aprovado? Edita a spec e registra a clarificação —
  não deixa o código divergir em silêncio.

## Índice

| # | Feature | Status |
|---|---|---|
| [000](./000-baseline/inventario.md) | Baseline — inventário do que já existe | Vivo (atualizar a cada feature) |
| [001](./001-racha-mvp/spec.md) | Racha — MVP | Entregue com pendências (ver inventário) |
| [002](./002-atualizacao-in-app/spec.md) | Atualização do app fora da Play Store (conteúdo OTA + APK retomável) | [Tarefas](./002-atualizacao-in-app/tasks.md) prontas — nada iniciado |

## Repositórios

- **Backend** (este repo): `F:\PROJETOS_DEV\Spring\sortifyteams`
- **Mobile**: `F:\PROJETOS_DEV\React Native\sortify-teams`

Uma spec pode cobrir os dois — as tarefas identificam o repositório com
`(BE)` / `(APP)`.
