# Template de ADR (formato MADR simplificado)

Arquivo: `docs/adr/NNNN-titulo-em-kebab-case.md`

```markdown
# ADR-NNNN: <Título no imperativo, ex.: Usar BigDecimal com DECIMAL128 para cálculos monetários>

- **Status:** Proposto | Aceito | Rejeitado | Substituído por ADR-XXXX
- **Data:** AAAA-MM-DD
- **Decisores:** <nome/papel>
- **Relacionado a:** <story BMAD, requisito do desafio, outros ADRs>

## Contexto
<Problema, forças em jogo e restrições. Cite o requisito do desafio que motiva.>

## Opções consideradas
1. **<Opção A>** — prós / contras / custo de reversão
2. **<Opção B>** — prós / contras / custo de reversão
3. **Não fazer nada** (quando aplicável)

## Decisão
<Opção escolhida e o porquê, ligado aos princípios: correção financeira > simplicidade > acoplamento > operabilidade.>

## Limites e regras resultantes
- <O que o módulo expõe / o que não pode depender de quê>

## Verificação
- <Teste ArchUnit, teste de integração, lint, constraint de banco que garante a decisão>

## Consequências
- Positivas: ...
- Negativas / dívida aceita: ...
- Gatilho para revisitar: <métrica ou evento que invalidaria a decisão>
```

## Exemplo curto

```markdown
# ADR-0004: Calcular potência fracionária com big-math em vez de Math.pow

- **Status:** Aceito
- **Data:** 2026-09-22
- **Relacionado a:** Requisito 3.2 (Motor de Precificação), ADR-0003

## Contexto
A fórmula VP = VF / (1 + i)^n usa n = dias/30, logo n é fracionário. BigDecimal.pow só aceita
expoente inteiro. Math.pow opera em double e introduz erro binário inaceitável em valores altos.

## Opções consideradas
1. Math.pow(double) — simples; erro de representação; reprovado em auditoria.
2. Arredondar n para meses inteiros — exato, mas distorce o preço de títulos curtos.
3. BigDecimalMath.pow (ch.obermuhlner:big-math) com DECIMAL128 — precisão arbitrária; +1 dependência.

## Decisão
Opção 3. Dependência pequena, madura e isolada atrás de `FinancialMath` no pacote shared.

## Verificação
- Teste parametrizado comparando com valores calculados em planilha de referência (tolerância 0,00).
- Regra ArchUnit: nenhuma classe fora de `shared.math` importa `ch.obermuhlner..`.

## Consequências
- Positivas: precisão determinística.
- Negativas: nova dependência a manter.
- Revisitar se: o negócio adotar convenção de dias úteis/252 (mudança de fórmula).
```
