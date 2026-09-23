# Riscos, premissas e aprovações necessárias

## Premissas usadas no planejamento

| ID | Premissa | Impacto se falsa |
|---|---|---|
| P-01 | Maven será usado, coerente com os gates prescritos. | Ajustar build, CI e documentação. |
| P-02 | BRL e USD têm duas casas decimais no MVP. | Generalizar escala/formatadores e casos de teste. |
| P-03 | Cada recebível tem id externo único dentro do cedente. | Redesenhar identidade e garantia de dupla liquidação. |
| P-04 | Taxa base é mensal, versionada por data de vigência. | Alterar fórmula, schema e API. |
| P-05 | Autenticação não é exigida na demonstração local. | Criar novo épico de segurança e modelo de autorização. |
| P-06 | Relatório usa consistência forte do mesmo PostgreSQL. | Redesenhar SLA/arquitetura de leitura. |
| P-07 | Uma implantação local é suficiente; escala de 1M tx/min é somente design. | Escopo e arquitetura deixam de caber no prazo. |

## Riscos

| ID | Prob. | Impacto | Risco | Mitigação proposta |
|---|---:|---:|---|---|
| R-01 | Média | Alto | Convenção financeira ambígua produz resultado “correto” tecnicamente e errado para o negócio. | Aprovar ADR-0003/0004 e casos de referência antes de código. |
| R-02 | Média | Alto | Optimistic locking isolado permite dupla liquidação com agregados distintos. | Constraint única por recebível + testes concorrentes adversos. |
| R-03 | Média | Alto | Prazo de 3–4 dias não comporta todo hardening sênior/especialista. | Priorizar cortes verticais; escala/EDA ficam documentais; Grafana pode ser opcional. |
| R-04 | Média | Médio | Meta de extrato não é reproduzível sem ambiente/dataset definidos. | Aprovar protocolo de benchmark e registrar hardware/plano. |
| R-05 | Baixa | Alto | Taxa velha ou invertida causa perda financeira. | Clock controlado, nomenclatura base/quote, testes de ambos os sentidos e bloqueio de expiração. |
| R-06 | Média | Médio | Dependência de matemática decimal ou jOOQ aumenta tempo de build/configuração. | Spike curto na story E0/E2 e isolamento atrás de API interna. |
| R-07 | Média | Médio | Diretório vazio `supabase/` induz implementação proibida/confunde avaliação. | Remoção humana aprovada antes da implementação; nenhum arquivo foi removido agora. |
| R-08 | Média | Médio | Skills BMAD de planejamento não estão instaladas, reduzindo validação automatizada do método. | Artefatos compatíveis foram produzidos; instalar/rodar validadores BMAD é decisão do usuário. |
| R-09 | Média | Médio | Repositório inteiro aparece untracked, dificultando distinguir baseline de mudanças. | Usuário deve revisar e estabelecer baseline; agente não executa Git mutável. |

## Decisões que precisam de aprovação

1. Aprovar ou rejeitar os ADRs 0001–0009; nenhum está aceito.
2. Confirmar dias corridos/30, taxa mensal, `DECIMAL128`, potência decimal e `HALF_EVEN` final.
3. Confirmar a convenção USD/BRL e idade máxima proposta de 15 minutos.
4. Confirmar que `(assignor_id, external_receivable_id)` identifica unicamente um recebível.
5. Confirmar a política definida de replay da resposta existente e `409 Conflict` para reutilização divergente da chave.
6. Confirmar que autenticação/autorização estão fora do MVP local.
7. Aprovar metas p95 e fornecer ambiente/dataset de referência.
8. Confirmar Prometheus/Grafana no Compose ou apenas endpoint Prometheus + logs nesta entrega.
9. Autorizar futuramente a remoção de `supabase/`; não realizada nesta fase.
10. Decidir se deseja instalar as skills BMAD específicas de planejamento para validar/normalizar estes artefatos antes da implementação.

## Gate de início da implementação

Nenhuma story deve entrar em desenvolvimento até que, no mínimo, ADR-0001 a ADR-0006 e as decisões 2–7 acima sejam resolvidos. Após a aprovação, os documentos devem ser atualizados para registrar as escolhas; só então E0-S1 pode começar.
