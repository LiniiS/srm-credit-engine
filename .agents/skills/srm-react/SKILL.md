---
name: srm-react
description: Implementa e revisa o frontend do SRM Credit Engine (React + TypeScript strict + Vite) com padrão sênior — arquitetura por features, separação entre componentes de UI e lógica (hooks/serviços), estado de servidor com TanStack Query, formulários com React Hook Form + Zod, simulação em tempo real com debounce e cancelamento, grid com paginação server-side e filtros sincronizados na URL, formatação monetária segura, acessibilidade e testes (Vitest, Testing Library, MSW). Use sempre que for criar, alterar, testar ou revisar qualquer coisa em /frontend — componentes, hooks, chamadas à API, estilos, config de build/lint, Dockerfile do front — ou ao executar dev-story/code-review do BMAD numa story de frontend.
---

# SRM Credit Engine — Frontend React

O painel é a ferramenta de trabalho da mesa de operações: precisa ser **correto** (nunca mostrar um valor errado com confiança), **responsivo** e **previsível**. O desafio avalia explicitamente a separação entre apresentação e lógica.

## Passo 0 — Carregar contexto (obrigatório)

1. Leia a story BMAD inteira e os ADRs citados (especialmente estado no front e contrato de API).
2. Leia o contrato OpenAPI do backend (`/v3/api-docs` ou arquivo exportado em `docs/api/`). O front consome o contrato; não inventa campos.
3. Leia os arquivos existentes da feature e seus testes; siga o padrão já presente.
4. Leia `references/arquitetura-front.md` sempre; `references/testes-front.md` antes de escrever testes.
5. Se precisar de nova biblioteca, nova store global ou mudar a estrutura de pastas: **HALT** e peça `srm-arquitetura`.

## Regras inegociáveis

- **Fonte única do cálculo:** o front **não** reimplementa a fórmula de precificação. A simulação chama `POST /api/v1/pricing/simulations`. Duplicar regra financeira no cliente viola DRY e cria divergência de centavos.
- **Dinheiro:** valores chegam como string decimal e permanecem string/decimal arbitrário. Nunca converta dinheiro com `Number`/`parseFloat`, nem mesmo apenas para exibir; use formatter seguro de string/`decimal.js` e `Intl.NumberFormat.formatToParts` para locale.
- **Entrada monetária:** input controlado que produz string normalizada (`"1234.56"`); validação Zod com regex de escala (até 2 casas) e limite máximo.
- **Tipagem:** `strict: true`, sem `any` (use `unknown` + narrowing). Tipos da API gerados do OpenAPI (`openapi-typescript`) — não escritos à mão.
- **Separação:** componentes em `components/` são puros (props in, eventos out, sem fetch). Lógica em hooks (`hooks/`) e acesso HTTP em `api/`. Páginas compõem.
- **Estado:** estado de servidor = TanStack Query; filtros/paginação = URL (search params); estado de formulário = React Hook Form; estado global de cliente só se houver necessidade real (YAGNI) — e aí Zustand pequeno e tipado.
- **Erros:** interprete `application/problem+json` num único lugar (cliente HTTP), exponha mensagens amigáveis; Error Boundary por rota; nenhum erro silencioso.
- **Segurança:** sem `dangerouslySetInnerHTML`; sem segredos no bundle (`VITE_*` é público); URL da API por variável de ambiente.
- **Acessibilidade:** labels associados, foco visível, erros anunciados (`aria-describedby`, `role="alert"`), carregamento/resultado em região `aria-live`, foco gerenciado após erro/alteração de rota, título de página, tabela semântica e navegação por teclado.

## Workflow (por task da story)

1. Traduza o AC em cenários de usuário (o que ele vê, digita, clica).
2. Escreva o teste do comportamento (Testing Library + MSW) — falhando.
3. Implemente: tipos/API → hook → componente de UI → composição na página.
4. Refatore: componentes > ~150 linhas ou com mais de uma responsabilidade são divididos.
5. Rode o gate. Atualize a story. Peça a `srm-git` o plano de commits; o usuário executa os commits.

## Funcionalidades do desafio — como implementar

**Painel do Operador (simulação em tempo real)**
- Form (RHF + Zod): valor de face, moeda do título, moeda de pagamento, vencimento, tipo (lista de `GET /receivable-types`).
- `useWatch` → debounce ~300 ms → `useQuery` com a entrada na `queryKey` (`enabled` só se o form for válido). Propague o `signal` para cancelar respostas obsoletas. Não preserve o resultado anterior durante uma nova simulação: marque-o como desatualizado ou substitua por estado de cálculo para não exibir preço velho como vigente.
- Exibir VP, deságio, taxa aplicada, taxa de câmbio e sua data; estados: ocioso, calculando, resultado, erro (com motivo do ProblemDetail).
- Botão "Liquidar" → `useMutation` com `Idempotency-Key` gerada **uma vez por intenção** (`crypto.randomUUID()`). Timeout/retry do mesmo payload reutiliza a chave; nova intenção após resposta definitiva gera outra. Desabilite durante envio e trate 409 com mensagem e ação de recarregar. O frontend reduz duplo clique; o backend garante idempotência.

**Grid de Transações**
- TanStack Table com `manualPagination`, `manualFiltering`, `manualSorting`.
- Filtros: período, cedente, moeda; sincronizados com a URL (`useSearchParams`), com debounce em campos de texto; mudar filtro volta para página 1.
- `useQuery` com `queryKey: ['settlements', filters, page, size, sort]` e `keepPreviousData`.
- Estados vazios e de erro explícitos; skeleton durante carregamento.

## Gate de verificação

```bash
cd frontend
npm run lint          # ESLint (typescript-eslint, react-hooks, jsx-a11y, import boundaries)
npm run typecheck     # tsc --noEmit
npm run test -- --run # Vitest + Testing Library + MSW (com cobertura)
npm run build         # build de produção
```
Critérios: tudo verde, zero `any` novo, zero `eslint-disable` sem justificativa em comentário, cobertura de `features/**/hooks` e `shared/lib` ≥ 80%.

Quando o ambiente completo estiver disponível, valide também o fluxo principal contra `docker compose up --build`; o frontend usa a API pelo endereço configurado no Compose.

## Checklist de revisão (modo code-review)

Classifique achados em **Bloqueante / Importante / Sugestão** com arquivo:linha:
- [ ] Alguma regra financeira calculada no cliente? `parseFloat`/`Number` em valor monetário?
- [ ] Componente de UI fazendo fetch ou contendo regra de negócio?
- [ ] Estado duplicado (mesmo dado em Query e em store/useState)?
- [ ] Efeitos (`useEffect`) usados para derivar estado que poderia ser calculado no render?
- [ ] Chaves de query estáveis e completas? Invalidação após mutation?
- [ ] Idempotency-Key reutilizada corretamente em reenvio? Botão protegido contra duplo clique?
- [ ] ProblemDetail tratado e mensagem útil ao operador?
- [ ] Acessibilidade (labels, foco, anúncio de erro, tabela semântica)?
- [ ] Testes testam comportamento do usuário, não detalhes de implementação?

## Integração com BMAD (dev-story)

- Edite na story apenas: Tasks/Subtasks, `Dev Agent Record` (modelo, debug log, completion notes, File List), `Change Log`, `Status`.
- Divergência entre story e contrato OpenAPI → HALT e reporte (não "ajuste" o contrato pelo front).
- Status `Review` somente com gate verde.

## Saída esperada ao final

```markdown
## Implementação
- Story: <id> — Tasks: <lista>
- Componentes (UI): <lista> | Hooks/lógica: <lista> | API: <lista>

## Verificações
- lint ✅ | typecheck ✅ | testes ✅ (<n>) | build ✅

## Decisões e dívidas
- <...>

## Próximo passo
- Commits sugeridos (via srm-git): <mensagens>
```
