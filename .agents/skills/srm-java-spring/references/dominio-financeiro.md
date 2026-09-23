# Domínio financeiro — precificação, dinheiro e câmbio

## Linguagem ubíqua (use estes nomes no código)

| Negócio (PT) | Código (EN) |
|---|---|
| Cedente | `Assignor` |
| Recebível / título | `Receivable` |
| Tipo de recebível (Duplicata, Cheque) | `ReceivableType` (`DUPLICATA_MERCANTIL`, `CHEQUE_PRE_DATADO`) |
| Valor de face | `faceValue` |
| Valor presente | `presentValue` |
| Deságio | `discount` |
| Taxa base / spread | `baseRate` / `spread` |
| Prazo | `term` |
| Liquidação / lote | `Settlement` / `SettlementBatch` |
| Taxa de câmbio | `ExchangeRate` |

## Value Objects

```java
public record Money(BigDecimal amount, CurrencyCode currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }
    public Money rounded() { // escala final, arredondamento único
        return new Money(amount.setScale(currency.minorUnits(), RoundingMode.HALF_EVEN), currency);
    }
    private void requireSameCurrency(Money o) {
        if (!currency.equals(o.currency)) throw new CurrencyMismatchException(currency, o.currency);
    }
}

public record Rate(BigDecimal value) { // taxa mensal como fração: 1,5% = 0.015
    public Rate {
        if (value.signum() < 0) throw new IllegalArgumentException("rate must be >= 0");
    }
    public Rate plus(Rate other) { return new Rate(value.add(other.value)); }
}
```

## Strategy de precificação

```java
public interface PricingStrategy {
    String key();                               // ex.: "DUPLICATA_MERCANTIL"
    Rate spread(PricingContext context);        // regra de risco
}

@Component
class DuplicataMercantilStrategy implements PricingStrategy {
    public String key() { return "DUPLICATA_MERCANTIL"; }
    public Rate spread(PricingContext ctx) { return ctx.configuredSpread(); } // 0.015 vindo do cadastro
}

@Component
class PricingStrategyRegistry {
    private final Map<String, PricingStrategy> byKey;
    PricingStrategyRegistry(List<PricingStrategy> strategies) {
        this.byKey = strategies.stream().collect(toUnmodifiableMap(PricingStrategy::key, identity()));
    }
    PricingStrategy resolve(String key) {
        var s = byKey.get(key);
        if (s == null) throw new UnsupportedReceivableTypeException(key);
        return s;
    }
}
```
Pontos de atenção:
- O **valor** do spread vem do cadastro (`receivable_type.spread_monthly`) para ser auditável/configurável; a **regra** (como o spread é determinado — fixo, por prazo, por rating) mora na strategy. Assim a Strategy tem razão de existir e não vira classe vazia.
- Registry falha rápido no startup se houver chave duplicada (`toUnmodifiableMap` lança) — teste isso.
- Teste de contrato parametrizado rodando todas as strategies registradas (LSP).

## Cálculo

```
n        = prazoDias / 30                (convenção do ADR)
fator    = (1 + taxaBase + spread) ^ n   (DECIMAL128, big-math para n fracionário)
VP       = VF / fator                    (DECIMAL128)
deságio  = VF − VP
Se moeda pagamento ≠ moeda título: VP_pag = converter(VP, taxa snapshot)
Arredondar VP_pag, deságio e líquido UMA vez (escala da moeda, HALF_EVEN)
```

```java
public final class FinancialMath {
    public static final MathContext MC = MathContext.DECIMAL128;
    private FinancialMath() {}
    public static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
        return BigDecimalMath.pow(base, exponent, MC);
    }
}
```

Regras de validação de domínio:
- VF > 0, com no máximo 2 casas.
- Vencimento > data de referência (Clock). Prazo 0 → rejeitar ou VP = VF (decidir no ADR e testar).
- Tipo de recebível ativo.
- Taxa de câmbio existente para o par e com idade ≤ limite.

## Câmbio

- Convenção: `rate(base, quote)` = unidades de `quote` por 1 `base`. Guarde sempre a direção.
- Converter BRL→USD com USD/BRL = 5.10: `valorUSD = valorBRL.divide(rate, MC)`.
- Não armazene taxa invertida; calcule a inversão no momento com DECIMAL128.
- A liquidação grava `fx_rate_id` **e** `fx_rate_applied` (snapshot).

## Casos de teste mínimos (tabela de referência)

Calcule os esperados numa planilha/calculadora de alta precisão e registre a origem no teste.

| Caso | Tipo | VF | Prazo (dias) | Base a.m. | Moeda título→pag. | Verifica |
|---|---|---|---|---|---|---|
| 1 | Duplicata | 10000.00 | 30 | 0.01 | BRL→BRL | n inteiro |
| 2 | Cheque | 10000.00 | 45 | 0.01 | BRL→BRL | n fracionário |
| 3 | Duplicata | 10000.00 | 30 | 0.01 | BRL→USD | conversão no fim |
| 4 | Duplicata | 0.01 | 1 | 0.01 | BRL→BRL | arredondamento mínimo |
| 5 | Duplicata | 99999999999999999.99 | 360 | 0.01 | BRL→BRL | sem overflow/perda |
| 6 | Tipo inexistente | — | — | — | — | exceção de domínio → 422 |
| 7 | Prazo passado | — | — | — | — | exceção de validação |
| 8 | Taxa de câmbio velha | — | — | — | BRL→USD | rejeição |
