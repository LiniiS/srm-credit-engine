# Testes do frontend

## Ferramentas
- **Vitest** (runner) + **@testing-library/react** + **@testing-library/user-event** + **jest-dom**.
- **MSW** para simular a API em nível de rede — os mesmos handlers servem para testes e para desenvolvimento offline.
- Opcional: Playwright para 1–2 fluxos E2E (simular → liquidar; filtrar grid) rodando contra o docker-compose.

## Princípios
- Teste o que o operador vê e faz: `getByRole`, `getByLabelText`; evite `getByTestId` salvo último recurso.
- Não teste detalhes de implementação (estado interno, nome de hook, número de renders).
- Um arquivo de teste ao lado do arquivo testado (`SimulationForm.test.tsx`).
- `QueryClient` novo por teste, com `retry: false`, via helper `renderWithProviders`.
- Timers falsos (`vi.useFakeTimers({ shouldAdvanceTime: true })`) para debounce.

## Cenários mínimos

**Painel do operador**
1. Preencher form válido → após debounce, exibe VP e deságio formatados em BRL.
2. Moeda de pagamento USD → exibe valor em USD e a taxa de câmbio usada.
3. Valor com 3 casas / negativo / vencimento passado → mensagem de validação, nenhuma chamada à API.
4. API retorna 422 → mensagem do ProblemDetail exibida com `role="alert"`.
5. Digitação rápida → apenas a última simulação é exibida (sem "flash" de valor antigo como definitivo).
6. Liquidar → envia `Idempotency-Key`; duplo clique gera uma única requisição; 409 mostra conflito.

**Grid**
1. Carrega página 1 com `size` padrão e exibe total.
2. Mudar filtro de moeda atualiza URL e volta para página 1.
3. Próxima página envia `page=1` ao servidor (paginação server-side verificada no handler MSW).
4. Resultado vazio → estado vazio; erro 500 → estado de erro com ação de tentar novamente.

## Exemplo

```tsx
it('exibe o valor presente após simular', async () => {
  server.use(http.post('*/api/v1/pricing/simulations', () =>
    HttpResponse.json({ presentValue: '9756.10', discount: '243.90', currency: 'BRL' })));
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  renderWithProviders(<PricingPage />);

  await user.type(screen.getByLabelText(/valor de face/i), '10000.00');
  await user.selectOptions(screen.getByLabelText(/tipo/i), 'DUPLICATA_MERCANTIL');
  await user.type(screen.getByLabelText(/vencimento/i), '2026-10-22');
  vi.advanceTimersByTime(300);

  expect(await screen.findByText('R$ 9.756,10')).toBeInTheDocument();
});
```
Obs.: `Intl` usa espaço não separável entre "R$" e o número; use matcher com regex ou normalize espaços.
