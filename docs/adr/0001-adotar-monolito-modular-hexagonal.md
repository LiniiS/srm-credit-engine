# ADR-0001: Adotar monólito modular com hexagonal seletiva

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** arquitetura em três camadas; restrições de stack

## Contexto

O backend precisa separar aplicação, negócio e persistência, preservar regras financeiras e caber em 3–4 dias. Distribuição prematura aumenta operação e consistência sem atender requisito atual.

## Opções consideradas

1. **Monólito modular com ports/adapters nas fronteiras reais** — simples de operar, mantém limites testáveis; reversão moderada.
2. **Microserviços por domínio** — isolamento forte; alto custo de rede, observabilidade e transações distribuídas.
3. **Monólito em camadas global** — entrega rápida; favorece acoplamento entre domínios e evolução difícil.

## Decisão

Propor opção 1, com módulos `currency`, `pricing`, `settlement`, `reporting` e `shared`. Hexagonal é aplicada onde isola regra, persistência ou integração; reporting usa duas camadas como permitido.

## Limites e regras resultantes

- `api → service → domain ← persistence`; domínio não depende de frameworks web/JPA.
- Módulos consomem somente APIs/ports públicos de outros módulos; ciclos são proibidos.
- Kafka, Redis e microserviços ficam fora da implementação atual.

## Verificação

- ArchUnit verifica camadas, ciclos e imports entre módulos.
- Testes unitários instanciam domínio/Strategies sem contexto Spring.

## Consequências

- Positivas: transação local, menor operação e extração futura possível.
- Negativas: disciplina de pacotes é necessária; um único deployment escala como unidade.
- Revisitar se: módulos exigirem escalabilidade/deploy independentes comprovados.

