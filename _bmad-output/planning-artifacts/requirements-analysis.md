# Análise de requisitos — SRM Credit Engine

- **Data:** 2026-09-23
- **Fase:** planejamento
- **Fonte normativa:** `docs/input/desafio-tecnico.md`

## Problema e objetivo

A mesa precisa antecipar recebíveis em BRL e USD com cálculo reproduzível, liquidação atômica e trilha auditável. O produto deve permitir simular preços, registrar taxas, liquidar lotes sem duplicidade e consultar extratos em volume, oferecendo uma experiência web operacional.

## Atores

| Ator | Necessidade |
|---|---|
| Operador da mesa | Manter câmbio, simular, liquidar lotes e acompanhar o resultado. |
| Auditoria/Compliance | Consultar extratos e reconstruir os parâmetros usados em uma liquidação. |
| Provedor de câmbio mock | Fornecer taxas para demonstrar integração externa e resiliência. |
| Avaliador/Desenvolvedor | Executar a solução localmente, compreender decisões e verificar qualidade. |

## Requisitos funcionais

| ID | Requisito | Prioridade MVP | Observação |
|---|---|---:|---|
| RF-01 | Cadastrar e consultar taxas de câmbio por par e vigência. | Must | Taxas são append-only para auditoria. |
| RF-02 | Sincronizar taxa por integração mockada. | Should | Nenhuma chamada externa dentro de transação de banco. |
| RF-03 | Manter tipos de recebível e selecionar regra de spread por Strategy. | Must | Duplicata 1,5% a.m. e cheque 2,5% a.m. como dados iniciais. |
| RF-04 | Simular valor presente sem persistir liquidação. | Must | Fórmula somente no backend. |
| RF-05 | Converter o valor presente quando a moeda de pagamento divergir da moeda do título. | Must | Conversão acontece ao final e usa convenção aprovada. |
| RF-06 | Liquidar lote de recebíveis atomicamente. | Must | Tudo ou nada; resultado auditável. |
| RF-07 | Tornar a liquidação idempotente e segura sob concorrência. | Must | Chaves diferentes não podem vencer sobre o mesmo recebível. |
| RF-08 | Consultar uma liquidação por identificador. | Must | Inclui snapshots financeiros aplicados. |
| RF-09 | Consultar extrato paginado por período, cedente e moeda. | Must | Server-side; leitura com jOOQ. |
| RF-10 | Operar painel web com formulário e simulação em tempo real. | Must | Debounce e cancelamento; erros acessíveis. |
| RF-11 | Operar grid histórico com paginação e filtros na URL. | Must | Estado de servidor separado da UI. |
| RF-12 | Expor contrato OpenAPI/Swagger. | Must | Erros em `application/problem+json`. |

## Requisitos não funcionais

| ID | Categoria | Requisito verificável |
|---|---|---|
| RNF-01 | Precisão | Dinheiro e taxas usam decimal; nenhum `double`, `float`, `Number` ou `parseFloat` no fluxo financeiro. |
| RNF-02 | Integridade | Liquidação é ACID e um recebível só pode ser liquidado uma vez. |
| RNF-03 | Concorrência | Em disputa simultânea pelo mesmo recebível, existe exatamente um vencedor. |
| RNF-04 | Segurança | Entradas são validadas e erros não expõem stack trace, SQL ou segredos. |
| RNF-05 | Desempenho | Extrato em base de 1 milhão de itens atende p95 local ≤ 300 ms para período+cedente, sujeito à aprovação do ambiente de referência. |
| RNF-06 | Usabilidade | Após 300 ms sem digitação, a simulação atualiza em p95 ≤ 500 ms no ambiente local de referência. |
| RNF-07 | Observabilidade | Logs estruturados correlacionáveis e métricas de latência, resultado e integração externa. |
| RNF-08 | Resiliência | Timeout, retry seletivo e circuit breaker protegem somente a integração de câmbio. |
| RNF-09 | Portabilidade | Frontend, backend e PostgreSQL sobem por Docker Compose. |
| RNF-10 | Testabilidade | Regras financeiras têm testes unitários; persistência/concorrência usam PostgreSQL 16 via Testcontainers. |
| RNF-11 | Arquitetura | Dependências de módulos/camadas são verificadas por ArchUnit; frontend usa TypeScript strict. |
| RNF-12 | Auditabilidade | Liquidação persiste taxas, spreads, câmbio, prazo e valores calculados como snapshots. |

## Restrições e não objetivos

- Stack obrigatória: Java 21, Spring Boot 3, React, TypeScript strict, Vite e PostgreSQL 16.
- Flyway é a fonte do schema; Testcontainers usa PostgreSQL real; jOOQ atende relatórios.
- Implementação atual é um monólito modular. Kafka, Redis, microserviços, Supabase e infraestrutura distribuída ficam fora.
- Design para 1 milhão de transações/minuto e EDA são documentação evolutiva, não código desta entrega.
- Autenticação/autorização, operação real de câmbio, Kubernetes e IaC não fazem parte do MVP, salvo aprovação de novo escopo.

## Lacunas que exigem decisão humana

1. Convenção de prazo e taxa base: dias corridos/30 e taxa mensal foram assumidos para proposta.
2. Arredondamento: `HALF_EVEN`, cálculo intermediário `DECIMAL128` e arredondamento final por moeda foram assumidos.
3. Câmbio: `base/quote` significa unidades de quote por 1 base; é preciso aprovar idade máxima da taxa.
4. Identidade do recebível: é necessário confirmar se existe identificador externo único por cedente.
5. Semântica de reenvio idempotente definida: mesma chave e payload reproduzem a resposta existente; payload divergente com a mesma chave gera `409 Conflict`.
6. Metas p95 dependem de hardware, dataset e protocolo de medição aprovados.
7. Autenticação está fora do desafio; confirmar se o risco é aceitável para a demonstração.
