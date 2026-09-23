# Definition of Ready e Definition of Done

## Definition of Ready — story

- Objetivo e valor do usuário são explícitos.
- Critérios Given/When/Then são verificáveis e ligados a IDs de requisito.
- Dependências e predecessoras estão concluídas ou planejadas.
- ADRs materiais relacionados estão `Aceito`; nenhum detalhe financeiro permanece ambíguo.
- Contrato de API/schema afetado foi aprovado e versionado no planejamento.
- Estratégia de teste cobre happy path, erros, limites e concorrência quando aplicável.
- Stories com UI identificam critérios WCAG 2.2 AA, cenários de teclado/foco/anúncio e roteiro manual com leitor de tela.
- Dados de teste e resultado financeiro de referência são conhecidos.
- Riscos de segurança, migração, rollback e observabilidade foram avaliados.
- A story cabe em uma sessão de implementação ou foi dividida verticalmente.

## Definition of Done — story

- Todos os critérios de aceite passam com evidência automatizada quando possível.
- Código e testes respeitam ADRs, arquitetura hexagonal útil e regras de dependência.
- Backend: `spotless:check` e `verify` passam; integração usa PostgreSQL via Testcontainers.
- Frontend: lint, typecheck, testes e build passam; Testing Library cobre teclado, foco, labels, títulos, regiões live, tabela e paginação; axe não encontra violações.
- Acessibilidade WCAG 2.2 AA é parte bloqueante da DoD: HTML semântico precede ARIA; navegação por teclado é completa; foco é visível e gerenciado em rotas, validação, erros e submissões.
- Labels, instruções e erros estão associados; resultados/estados assíncronos são anunciados; título de cada página é atualizado; tabela e paginação possuem semântica acessível.
- Contraste atende AA, informação não depende apenas de cor e `prefers-reduced-motion` é respeitado.
- Fluxos afetados foram verificados manualmente somente por teclado e com leitor de tela; evidência e achados foram registrados, sem bloqueantes abertos.
- Contratos OpenAPI, migrações Flyway e documentação afetada estão sincronizados.
- Sem `double`/`float`/`Number`/`parseFloat` em dinheiro ou taxas; sem segredos e sem erros internos expostos.
- Logs/métricas necessários existem e não vazam dados sensíveis.
- Revisão não deixa achado material aberto.
- Story/AI_USAGE registram arquivos, testes reais, decisões, riscos e trabalho diferido.
- Plano de commits atômicos está preparado para execução humana; o Codex não executa commits.

## Definition of Done — release

- Todas as stories MVP satisfazem a DoD e a rastreabilidade não tem lacunas.
- Compose sobe do zero e smoke test ponta a ponta passa.
- Gate documental em modo `release` passa.
- README, C4, ER, DDL, ADRs, observabilidade, escala, EDA e AI_USAGE refletem o sistema real.
- Vulnerabilidades/dependências e recuperação de falhas foram revisadas.
- Tag SemVer e simulação de crise são executadas/documentadas exclusivamente pelo responsável humano.
