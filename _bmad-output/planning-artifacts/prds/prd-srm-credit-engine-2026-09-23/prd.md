# PRD — SRM Credit Engine

- **Versão:** 0.1
- **Status:** Proposto para aprovação
- **Data:** 2026-09-23

## Visão do produto

Uma aplicação local demonstrável que permite à mesa precificar e liquidar recebíveis multimoedas com precisão decimal, integridade transacional e rastreabilidade, e permite à auditoria consultar o histórico em volume.

## Resultado esperado

Ao final do MVP, um avaliador consegue subir a solução por Docker Compose, cadastrar/sincronizar câmbio, simular uma antecipação, liquidar um lote uma única vez e localizar o resultado no extrato, enquanto testes demonstram precisão, atomicidade e concorrência.

## Personas e jornadas

### Operador da mesa

1. Confere ou atualiza a taxa USD/BRL.
2. Informa cedente, recebíveis, moeda de pagamento e vencimentos.
3. Recebe simulação com valor presente, deságio e conversão.
4. Confirma a liquidação; retries não duplicam o negócio.
5. Consulta o lote no histórico.

### Auditoria/Compliance

1. Filtra extrato por período, cedente e moeda.
2. Abre uma liquidação.
3. Reconstrói o cálculo pelos snapshots de taxa base, spread, prazo, câmbio e arredondamento.

## Capacidades

| ID | Capacidade | Critério de sucesso |
|---|---|---|
| CAP-01 | Gestão de câmbio | Taxas versionadas por vigência e integração mock resiliente. |
| CAP-02 | Precificação por risco | Estratégia correta por tipo e resultados determinísticos. |
| CAP-03 | Simulação cross-currency | Conversão final rastreável sem persistir liquidação. |
| CAP-04 | Liquidação segura | ACID, idempotência e um único vencedor por recebível. |
| CAP-05 | Extrato analítico | Filtros e paginação server-side com jOOQ. |
| CAP-06 | Experiência do operador | Simulação reativa e histórico acessível. |
| CAP-07 | Operabilidade | OpenAPI, erros seguros, logs, métricas, CI e Compose. |

## Escopo MVP

Inclui RF-01 a RF-12 e RNF-01 a RNF-12 definidos na análise. O corte vertical prioriza primeiro um caminho executável, depois precisão/câmbio, liquidação, extrato, frontend e hardening.

## Fora do escopo

- Autenticação, autorização e segregação por fundo.
- Provedor real de mercado, execução financeira ou conciliação bancária.
- Kafka, Redis, microserviços, Supabase, event sourcing e deployment cloud.
- Moedas além de BRL/USD na experiência inicial, embora o modelo permita expansão controlada.
- Edição retroativa de taxa ou liquidação concluída.

## Regras de negócio

1. `VP = VF / (1 + taxaBase + spread)^n`; `n` e arredondamento dependem da aprovação do ADR-0003.
2. O spread é resolvido por tipo de recebível via Strategy.
3. A conversão cambial ocorre depois do VP e usa a taxa vigente não expirada.
4. Liquidação grava snapshots de todos os parâmetros financeiros.
5. O lote é persistido por inteiro ou não é persistido.
6. Um recebível identificado externamente pode integrar no máximo uma liquidação concluída.
7. Mesma `Idempotency-Key` e mesmo payload reproduzem a resposta existente; payload diferente com a mesma chave é conflito e retorna 409.

## Métricas de sucesso

- 100% dos casos financeiros de referência passam sem tolerância no centavo final.
- Teste concorrente produz exatamente um vencedor.
- Todos os requisitos têm story e verificação rastreáveis.
- Gates backend/frontend/documentação e smoke test do Compose passam.
- Extrato cumpre o p95 aprovado no ambiente de referência.

## Dependências e restrições

Java 21, Spring Boot 3, React, TypeScript strict, Vite, PostgreSQL 16, Docker Compose, Flyway, Testcontainers e jOOQ. Dependências adicionais materiais só entram após ADR aceito.

## Aprovação necessária

O PRD só fica pronto para implementação após aprovação das convenções financeiras, identidade do recebível, política de câmbio expirado, metas mensuráveis e ADRs propostos.
