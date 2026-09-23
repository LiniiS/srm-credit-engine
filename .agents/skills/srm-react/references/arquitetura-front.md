# Arquitetura do frontend

## Estrutura de pastas (feature-based)

```
frontend/src/
  app/                    # bootstrap: providers (QueryClient, Router, ErrorBoundary), rotas, layout
  features/
    pricing/              # Painel do operador (simulação + liquidação)
      api/                # funções HTTP da feature (usam shared/api/http)
      hooks/              # useSimulation, useSettle, useReceivableTypes
      components/         # SimulationForm, SimulationResult (puros)
      schemas/            # Zod
      PricingPage.tsx     # composição
    settlements/          # Grid de transações
      api/ hooks/ components/ SettlementsPage.tsx
    exchange-rates/       # Consulta/atualização manual de taxas
  shared/
    api/                  # http client (fetch wrapper), ProblemDetail, tipos gerados (schema.d.ts)
    ui/                   # design system mínimo: Button, Input, MoneyText, DataTable, Alert
    lib/                  # formatMoney, formatDate, debounce, idempotency
    hooks/                # useDebouncedValue, useUrlState
```
Regra de dependência: `features/*` → `shared/*`; **nunca** feature → outra feature (se precisar compartilhar, sobe para `shared`). `shared/ui` não conhece `features`. Forçar com `eslint-plugin-boundaries` ou `no-restricted-imports`.

## Padrões

| Padrão | Uso | Motivo |
|---|---|---|
| Container/Presentational (via hooks) | Página/hook fazem dados; componente só renderiza | Separação exigida pelo desafio; teste de UI sem rede |
| Custom hooks | `useSimulation(input)` encapsula debounce + query | Lógica reutilizável e testável isolada |
| Adapter de API | `shared/api/http.ts` converte erro HTTP → `ApiError` com ProblemDetail | Tratamento de erro em um lugar |
| Schema-first | Zod define forma e mensagens; tipo inferido com `z.infer` | Uma fonte para validação e tipo do form |
| URL as state | Filtros/paginação em search params | Links compartilháveis, back/forward funcionam, sem store global |

## Cliente HTTP

```ts
export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail) { super(problem.detail ?? problem.title); }
}

export async function http<T>(path: string, init: RequestInit & { signal?: AbortSignal } = {}): Promise<T> {
  const res = await fetch(`${import.meta.env.VITE_API_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...init.headers },
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => ({ title: 'Erro inesperado', status: res.status }));
    throw new ApiError(res.status, problem as ProblemDetail);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}
```

## Hook de simulação

```ts
export function useSimulation(input: SimulationInput | null) {
  const debounced = useDebouncedValue(input, 300);
  return useQuery({
    queryKey: ['simulation', debounced],
    queryFn: ({ signal }) => simulate(debounced!, signal),
    enabled: debounced !== null,
    placeholderData: keepPreviousData,
    staleTime: 10_000,
    retry: (count, err) => !(err instanceof ApiError && err.status < 500) && count < 2,
  });
}
```
Não faça retry em 4xx (erro do usuário) — só em 5xx/rede.

## Formatação monetária

```ts
export function formatMoney(amount: string, currency: string, locale = 'pt-BR') {
  // Intl aceita string decimal em navegadores modernos? Não de forma garantida — converta com cuidado:
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(Number(amount));
}
```
`Number()` aqui é aceitável **somente para exibição** de valores dentro da faixa segura (até ~15 dígitos significativos). Para valores maiores, formate a string manualmente ou com `decimal.js` (`toFixed` + separadores). Documente o limite; nunca faça conta com o resultado.

## Estado global

Comece sem store global. Critérios para introduzir Zustand: dado de cliente (não de servidor) compartilhado por ≥2 rotas e que não cabe na URL (ex.: preferências do operador). Se criar, uma store por preocupação, com seletores tipados, e registre o motivo na story/ADR.

## Estilo e UI

- Uma abordagem só (CSS Modules, Tailwind ou lib de componentes) — decidir em ADR.
- Componentes de `shared/ui` com props mínimas e acessíveis por padrão.
- Números alinhados à direita, fonte tabular (`font-variant-numeric: tabular-nums`) em colunas monetárias.
