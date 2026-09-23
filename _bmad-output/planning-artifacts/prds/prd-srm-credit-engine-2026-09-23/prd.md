# PRD — SRM Credit Engine

- **Versão:** 1.0
- **Status:** Aprovado para implementação por stories
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
| CAP-06 | Experiência do operador | Simulação e histórico atendem WCAG 2.2 nível AA, inclusive teclado e leitor de tela. |
| CAP-07 | Operabilidade | OpenAPI, erros seguros, logs, métricas, CI e Compose. |

## Escopo MVP

Inclui RF-01 a RF-12 e RNF-01 a RNF-13 definidos na análise. O corte vertical prioriza primeiro um caminho executável, depois precisão/câmbio, liquidação, extrato, frontend e hardening.

## Fora do escopo

- Autenticação, autorização e segregação por fundo.
- Provedor real de mercado, execução financeira ou conciliação bancária.
- Kafka, Redis, microserviços, Supabase, event sourcing e deployment cloud.
- Moedas além de BRL/USD na experiência inicial, embora o modelo permita expansão controlada.
- Edição retroativa de taxa ou liquidação concluída.

## Regras de negócio

1. `VP = VF / (1 + taxaBase + spread)^n`; pela convenção ACT/30, o vencimento é ajustado ao próximo dia útil no calendário brasileiro configurável, `termDays` usa dias corridos reais até a data ajustada e `n = termDays / 30`.
2. O spread é resolvido por tipo de recebível via Strategy.
3. A conversão cambial ocorre depois do VP e usa a taxa vigente não expirada.
4. Liquidação grava snapshots de todos os parâmetros financeiros.
5. O lote é persistido por inteiro ou não é persistido.
6. Um recebível identificado externamente pode integrar no máximo uma liquidação concluída.
7. Mesma `Idempotency-Key` e mesmo payload reproduzem a resposta existente; payload diferente com a mesma chave é conflito e retorna 409.
8. Taxa base é mensal, por moeda e versionada por vigência; seeds iniciais são fictícios e identificados como demonstração.
9. Cálculo intermediário usa DECIMAL128 e potência decimal; o resultado final arredonda uma única vez com `HALF_EVEN`.
10. Câmbio usa BASE/QUOTE, conversão após o VP, snapshot da taxa e validade configurável inicialmente em 15 minutos.
11. O recebível é identificado por `(assignor_id, external_id)`, tem estado persistente e não pode integrar duas liquidações vencedoras.

## Requisitos de acessibilidade

1. Todo o MVP web atende WCAG 2.2 nível AA; HTML semântico e controles nativos precedem qualquer uso de ARIA.
2. Navegação, formulários, tabela, ordenação e paginação são completos por teclado, sem armadilha e com foco visível.
3. Mudanças de rota atualizam o título da página e posicionam o foco no conteúdo principal. Validação, erros e submissões têm gerenciamento de foco previsível.
4. Campos possuem labels, instruções e erros programaticamente associados; erro de submissão leva ao primeiro campo inválido.
5. Resultados de simulação, confirmações e estados assíncronos são anunciados em regiões `aria-live` adequadas, sem excesso de anúncios.
6. O extrato usa tabela semântica e paginação com nomes/estado atual acessíveis.
7. Texto, componentes, foco e estados atendem contraste AA; informação não depende somente de cor; animações respeitam `prefers-reduced-motion`.
8. Testing Library e axe verificam automaticamente os comportamentos cobertos; cada fluxo primário também passa por verificação manual com teclado e leitor de tela.

## Métricas de sucesso

- 100% dos casos financeiros de referência passam sem tolerância no centavo final.
- Teste concorrente produz exatamente um vencedor.
- Todos os requisitos têm story e verificação rastreáveis.
- Gates backend/frontend/documentação e smoke test do Compose passam.
- Extrato com 1 milhão de registros cumpre p95 local ≤ 300 ms sob protocolo documentado.
- Nenhum fluxo primário possui violação axe; checklist manual WCAG 2.2 AA por teclado e leitor de tela está concluído sem bloqueantes.

## Dependências e restrições

Java 21, Spring Boot 3, React, TypeScript strict, Vite, PostgreSQL 16, Docker Compose, Flyway, Testcontainers e jOOQ. Logs são JSON; métricas usam Prometheus e Prometheus/Grafana ficam disponíveis em profile opcional. Autenticação e autorização estão fora do MVP e são limitação conhecida. Dependências adicionais materiais exigem nova decisão arquitetural.

## Aprovação

As convenções financeiras, identidade do recebível, política cambial, meta de benchmark, acessibilidade, arquitetura e fluxo Git foram aprovados pela responsável e registrados nos ADRs 0001–0009.
