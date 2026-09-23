# ADR-0003: Padronizar cálculo financeiro decimal

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** RF-03 a RF-05, RNF-01 e RNF-12

## Contexto

A fórmula usa dinheiro, taxas mensais e prazo potencialmente fracionário. Erro binário ou arredondamento intermediário inviabiliza auditoria. A convenção de prazo não foi fornecida pelo desafio.

## Opções consideradas

1. **`BigDecimal`, DECIMAL128, dias corridos/30, potência decimal e HALF_EVEN final** — precisão e prazo proporcional; exige biblioteca matemática isolada.
2. **`BigDecimal.pow(int)` com meses inteiros** — sem dependência; distorce prazos quebrados.
3. **`Math.pow(double)`** — simples; erro binário inaceitável.
4. **Dias úteis/252** — prática possível, mas exige calendário e taxa compatível não especificados.

## Decisão

Propor opção 1. `n = termDays / 30`; taxa base e spread são mensais; intermediários usam DECIMAL128; dinheiro arredonda uma vez para os minor units da moeda com HALF_EVEN. A dependência de potência decimal só entra após aprovação.

## Limites e regras resultantes

- Fórmula existe somente no backend; Strategy fornece spread, não replica o cálculo.
- `double`, `float`, `Number` e `parseFloat` são proibidos para dinheiro/taxas.
- Snapshots persistem prazo, taxa base, spread, VP, deságio e resultado.

## Verificação

- Casos aprovados por planilha/fonte independente com igualdade no centavo final.
- ArchUnit/static checks restringem matemática externa ao adapter `FinancialMath`.

## Consequências

- Positivas: determinismo e auditabilidade.
- Negativas: dependência adicional e custo computacional maior.
- Revisitar se: negócio aprovar convenção 21/252 ou calendário específico.

