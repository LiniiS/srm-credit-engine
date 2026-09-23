# ADR-0007: Adotar observabilidade e resiliência seletiva

- **Status:** Aceito
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** requisitos sênior de observabilidade e resiliência

## Contexto

Falhas de câmbio devem ser controladas e operações financeiras precisam ser diagnosticáveis. Retry indiscriminado pode duplicar efeitos ou amplificar falhas.

## Opções consideradas

1. **Micrometer, logs JSON, tracing e Resilience4j somente no provider de FX** — observável e delimitado; dependências extras.
2. **Logs texto sem métricas** — simples; insuficiente para operação.
3. **Retry global** — configuração fácil; perigoso para comandos não idempotentes.

## Decisão

Adotar a opção 1. Timeout, retry com backoff para falhas transitórias e circuit breaker ficam no adapter FX. Settlement não recebe retry automático. Logs são estruturados em JSON; métricas são publicadas via Micrometer/Prometheus; Prometheus e Grafana integram o Compose em profile opcional de observabilidade.

## Limites e regras resultantes

- Logs incluem trace/span, outcome e ids técnicos; documento do cedente é mascarado.
- Métricas não usam identificadores de alta cardinalidade.
- Fallback nunca aceita taxa expirada.

## Verificação

- Testes com mock server e clock; scrape de métricas; inspeção de logs sem PII/segredos.

## Consequências

- Positivas: falhas explicáveis e integração protegida.
- Negativas: tuning e Compose mais pesados.
- Revisitar se: SLOs ou plataforma corporativa definirem outra stack.
