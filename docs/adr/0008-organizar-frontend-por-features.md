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

Propor opção 1. TanStack Query guarda estado remoto; formulário/URL guardam estado local compartilhável; debounce de 300 ms e AbortSignal cancelam simulações obsoletas. A SPA deve atender WCAG 2.2 nível AA em todos os fluxos do MVP.

## Limites e regras resultantes

- Fórmula e conversão financeira existem somente no backend.
- `features/*` não importam internals umas das outras; composição ocorre em `app`.
- Dinheiro permanece string do transporte à formatação decimal segura.
- HTML semântico e controles nativos são a primeira escolha; ARIA só complementa semântica ausente e não substitui elementos nativos.
- Todos os fluxos são operáveis por teclado, sem armadilhas, com ordem lógica e foco visível conforme WCAG 2.2 AA.
- O roteador atualiza o título da página e move o foco para o conteúdo principal em cada navegação. Erros de validação focam o primeiro campo inválido; falhas ou sucesso de submissão recebem foco ou anúncio coerente, sem deslocamento inesperado.
- Labels, instruções e erros são programaticamente associados aos campos. Resultados de simulação e estados assíncronos usam região `aria-live` com prioridade adequada, sem anúncios duplicados.
- O extrato usa tabela semântica com caption/cabeçalhos; ordenação comunica estado. A paginação possui nome acessível, página atual e controles nativos desabilitados quando indisponíveis.
- Cores e estados visuais atendem contraste AA e nunca são a única pista. Animações/transições respeitam `prefers-reduced-motion`.

## Verificação

- ESLint boundaries, TypeScript strict e `jsx-a11y`.
- Testing Library + `user-event` verificam comportamento por role/label, teclado, foco, títulos, anúncios, tabela e paginação; axe não admite violações automatizáveis.
- Verificação manual de cada fluxo primário somente por teclado e com leitor de tela, além de contraste e `prefers-reduced-motion`.

## Consequências

- Positivas: cache, cancelamento, testes previsíveis e acesso equivalente aos fluxos críticos.
- Negativas: curva inicial, disciplina de chaves/query invalidation e verificação manual de acessibilidade que não pode ser substituída por axe.
- Revisitar se: escopo da SPA se tornar trivial ou exigir estado offline complexo.
