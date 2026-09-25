# Contratos principais da API — proposta

- **Base path:** `/api/v1`
- **Mídia:** `application/json`; erros `application/problem+json`
- **Decimais:** dinheiro e taxas são strings JSON, nunca números binários
- **Datas:** ISO-8601; instantes com offset/UTC
- **Paginação:** `page >= 0`, `1 <= size <= 100`

## Endpoints

| Método | Rota | Sucesso | Erros principais |
|---|---|---|---|
| POST | `/exchange-rates` | 201 + `Location` | 400 |
| POST | `/exchange-rates/sync` | 202 + `Location` | 400, 503 |
| GET | `/exchange-rates/latest?base&quote` | 200 | 400, 404 |
| GET | `/receivable-types` | 200 | — |
| POST | `/pricing/simulations` | 200 | 400, 422, 503 |
| POST | `/settlements` | 201 + `Location` | 400, 409, 422, 503 |
| GET | `/settlements/{id}` | 200 | 404 |
| GET | `/reports/settlements?from&to&assignorId&currency&page&size&sort` | 200 | 400 |

## Cadastrar taxa

```json
{
  "baseCurrency": "USD",
  "quoteCurrency": "BRL",
  "rate": "5.10000000",
  "effectiveAt": "2026-09-23T12:00:00Z",
  "source": "MANUAL"
}
```

## Sincronizar taxa

```http
POST /api/v1/exchange-rates/sync
Content-Type: application/json

{"baseCurrency":"USD","quoteCurrency":"BRL"}
```

O sucesso retorna `202 Accepted`, `Location` e o mesmo DTO decimal do cadastro manual. Entrada inválida ou moeda fora do catálogo retorna `400`; timeout, circuito aberto, falha HTTP ou payload externo inválido retorna `503 application/problem+json` com `code=FX_PROVIDER_UNAVAILABLE`. A resposta pública nunca inclui URL, classe, stack trace ou payload do provider.

O provider local recebe somente `base` e `quote`; cenários de teste são selecionados pela API administrativa do WireMock, nunca por parâmetros ou headers enviados pelo backend.

## Simular precificação

```json
{
  "paymentCurrency": "USD",
  "items": [
    {
      "externalReceivableId": "REC-2026-0001",
      "receivableType": "DUPLICATA_MERCANTIL",
      "faceValue": "10000.00",
      "faceCurrency": "BRL",
      "dueDate": "2026-12-23"
    }
  ]
}
```

Resposta proposta:

```json
{
  "calculationDate": "2026-09-23",
  "paymentCurrency": "USD",
  "totalNetValue": "0.00",
  "items": [
    {
      "externalReceivableId": "REC-2026-0001",
      "termDays": 91,
      "baseRateApplied": "0.000000",
      "spreadApplied": "0.015000",
      "presentValueInFaceCurrency": "0.00",
      "discountInFaceCurrency": "0.00",
      "fxPair": "USD/BRL",
      "fxRateApplied": "5.10000000",
      "netValue": "0.00"
    }
  ]
}
```

Os zeros são placeholders de formato, não exemplos financeiros aprovados.

## Liquidar lote

Usa header obrigatório `Idempotency-Key` e o mesmo corpo da simulação, acrescido de:

```json
{
  "assignorId": "018f2f25-7b9d-7c2e-a6f7-5b6e1d3a9012",
  "paymentCurrency": "USD",
  "items": []
}
```

- Primeiro processamento: `201 Created`.
- Repetição com a mesma chave e payload equivalente: replay da resposta existente, com o mesmo status, corpo e header `Location` persistidos para a operação original.
- Mesma chave com payload diferente: `409 Conflict`, código `IDEMPOTENCY_KEY_REUSED`.
- Recebível já liquidado/conflito concorrente: `409 Conflict`.

## Extrato

`from` e `to` são obrigatórios. `assignorId` e `currency` são opcionais. Ordenação permitida inicialmente: `settledAt,desc|asc`.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## ProblemDetail

```json
{
  "type": "https://srm.example/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/exchange-rates",
  "code": "VALIDATION_ERROR",
  "violations": [{"field": "quoteCurrency", "message": "deve ser diferente de baseCurrency"}]
}
```

`violations` é retornado nos erros de validação e contém `field` e `message`. O MVP não promete `traceId`, pois ainda não possui correlação real de tracing/logs.

O contrato final será gerado/validado por OpenAPI durante implementação. Entidades de persistência e detalhes internos nunca fazem parte da API.
