# ADR-0003: Padronizar cálculo financeiro decimal

- **Status:** Aceito
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** RF-03 a RF-05, RNF-01 e RNF-12

## Contexto

A fórmula usa dinheiro, taxas mensais e prazo potencialmente fracionário. Erro binário, arredondamento intermediário ou tratamento ambíguo de vencimentos inviabiliza auditoria. A taxa base precisa variar por moeda e vigência.

## Opções consideradas

1. **`BigDecimal`, DECIMAL128, ACT/30, vencimento ajustado, potência decimal e HALF_EVEN final** — precisão e prazo proporcional; exige calendário e biblioteca matemática isolados.
2. **`BigDecimal.pow(int)` com meses inteiros** — sem dependência; distorce prazos quebrados.
3. **`Math.pow(double)`** — simples; erro binário inaceitável.
4. **Dias úteis/252** — prática possível, mas exige calendário e taxa compatível não especificados.

## Decisão

Adotar a opção 1. A convenção é ACT/30: primeiro ajusta-se o vencimento para o próximo dia útil conforme calendário brasileiro configurável; depois `termDays` é a quantidade real de dias corridos entre a data de cálculo e o vencimento ajustado, e `n = termDays / 30`. Taxa base e spread são mensais. A taxa base é versionada por moeda e vigência, inicialmente alimentada por seeds fictícios explícitos. Intermediários usam `MathContext.DECIMAL128`, a potência é calculada em decimal e o dinheiro é arredondado uma única vez no resultado final para os minor units da moeda com `HALF_EVEN`.

## Limites e regras resultantes

- Fórmula existe somente no backend; Strategy fornece spread, não replica o cálculo.
- `double`, `float`, `Number` e `parseFloat` são proibidos para dinheiro/taxas.
- Snapshots persistem prazo, taxa base, spread, VP, deságio e resultado.
- Calendário de dias úteis é uma porta configurável; a implementação inicial usa calendário brasileiro com fins de semana e feriados configurados.
- Seeds de taxa base são fictícios e identificados como dados de demonstração, nunca como taxas oficiais.

## Verificação

- Casos aprovados por planilha/fonte independente com igualdade no centavo final.
- ArchUnit/static checks restringem matemática externa ao adapter `FinancialMath`.
- Testes cobrem vencimento em sábado, domingo e feriado configurado, virada de mês/ano, taxa base por moeda/vigência e arredondamento `HALF_EVEN`.

## Consequências

- Positivas: determinismo e auditabilidade.
- Negativas: dependência adicional e custo computacional maior.
- Revisitar se: o negócio substituir ACT/30, adotar outro calendário ou fornecer fonte oficial para taxas base.
