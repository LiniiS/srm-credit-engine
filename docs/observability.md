# Observabilidade

## Precificação

Cada chamada de `POST /api/v1/pricing/simulations` registra o timer Micrometer
`srm.pricing.duration`. Logs estruturados registram `outcome` e, no sucesso,
`currency` e `receivableType`; em falha registram somente o código estável. Valores
monetários e payloads não são usados como tags nem campos de log; identificadores
de alta cardinalidade nunca são tags de métricas.

Conversões cambiais recusadas incrementam
`srm.fx.conversion.failures{reason=not_found|expired}`. As únicas tags são motivos
técnicos de baixa cardinalidade. O log correspondente informa o par solicitado;
para expiração também registra `ageSeconds` e `exchangeRateId`. Simulações na mesma
moeda não consultam a porta cambial e não emitem métrica ou log de conversão FX.

As métricas podem ser inspecionadas no endpoint Actuator `metrics`; a exposição
Prometheus será habilitada quando o profile de observabilidade planejado for
implementado. Readiness permanece em `/actuator/health/readiness`.

## Calendário

O recurso `calendars/anbima-brazil-2025-2030.csv` registra fonte ANBIMA, cobertura e
data de consulta (`2026-09-26`). Não há chamada externa em runtime.
