# ADR-0006: Padronizar contratos e erros HTTP

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** RF-12, RNF-04

## Contexto

Frontend e auditoria precisam de contrato estável, status semânticos e erros seguros. Entidades persistentes não podem vazar.

## Opções consideradas

1. **REST `/api/v1`, DTOs, OpenAPI e RFC 9457 `ProblemDetail`** — padrão interoperável; requer disciplina de mapeamento.
2. **Erros ad hoc** — rápido; inconsistente e inseguro.
3. **GraphQL** — flexível; desnecessário para fluxos e paginação definidos.

## Decisão

Propor opção 1. Decimais trafegam como strings; datas ISO; respostas paginadas são explícitas; códigos de domínio complementam status HTTP.

## Limites e regras resultantes

- 400 para sintaxe/validação, 404 ausente, 409 conflito, 422 regra, 503 dependência indisponível.
- `traceId` pode ser exposto; stack, SQL e mensagens internas não.
- OpenAPI é validado, mas não substitui testes de contrato.

## Verificação

- Testes MockMvc/integração por erro e validação automatizada do OpenAPI.

## Consequências

- Positivas: cliente previsível e diagnóstico seguro.
- Negativas: DTOs/mappers adicionais.
- Revisitar se: houver consumidores externos com versionamento incompatível.

