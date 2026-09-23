# ADR-0008: Organizar frontend por features

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** painel, grid e separação UI/estado

## Contexto

A SPA precisa separar UI, estado de servidor, validação e transporte sem duplicar regras financeiras. Filtros devem sobreviver a reload e links.

## Opções consideradas

1. **Features + TanStack Query + React Hook Form/Zod + URL para filtros** — responsabilidades claras; mais bibliotecas.
2. **Context global para tudo** — baixo início; cache/invalidação e renders difíceis.
3. **Estado local e fetch manual** — poucas dependências; repetição e corrida de requisições.

## Decisão

Propor opção 1. TanStack Query guarda estado remoto; formulário/URL guardam estado local compartilhável; debounce de 300 ms e AbortSignal cancelam simulações obsoletas.

## Limites e regras resultantes

- Fórmula e conversão financeira existem somente no backend.
- `features/*` não importam internals umas das outras; composição ocorre em `app`.
- Dinheiro permanece string do transporte à formatação decimal segura.

## Verificação

- ESLint boundaries, TypeScript strict, MSW, Testing Library e axe.

## Consequências

- Positivas: cache, cancelamento e testes previsíveis.
- Negativas: curva inicial e disciplina de chaves/query invalidation.
- Revisitar se: escopo da SPA se tornar trivial ou exigir estado offline complexo.

