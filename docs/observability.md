# Observabilidade

## Precificação

Cada chamada de `POST /api/v1/pricing/simulations` registra o timer Micrometer
`srm.pricing.duration`. Logs estruturados registram `outcome` e, no sucesso,
`currency` e `receivableType`; em falha registram somente o código estável. Valores
monetários, payloads e identificadores de alta cardinalidade não são usados como
tags nem campos de log.

As métricas podem ser inspecionadas no endpoint Actuator `metrics`; a exposição
Prometheus será habilitada quando o profile de observabilidade planejado for
implementado. Readiness permanece em `/actuator/health/readiness`.

## Calendário

O recurso `calendars/anbima-brazil-2025-2030.csv` registra fonte ANBIMA, cobertura e
data de consulta (`2026-09-26`). Não há chamada externa em runtime.
