package com.srm.creditengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.pricing.domain.PricingStrategy;
import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingStrategyTest {

  @Test
  void initial_strategies_return_the_exact_approved_monthly_spreads() {
    assertThat(new DuplicataMercantilPricingStrategy().monthlySpread())
        .isEqualByComparingTo("0.015");
    assertThat(new ChequePreDatadoPricingStrategy().monthlySpread()).isEqualByComparingTo("0.025");
  }

  @Test
  void registry_resolves_by_stable_key_without_fallback() {
    var registry =
        new PricingStrategyRegistry(
            List.of(new DuplicataMercantilPricingStrategy(), new ChequePreDatadoPricingStrategy()));

    assertThat(registry.resolve("DUPLICATA_MERCANTIL").monthlySpread())
        .isEqualByComparingTo("0.015");
    assertThatThrownBy(() -> registry.resolve("CONTRATO"))
        .isInstanceOf(PricingStrategyNotConfiguredException.class)
        .hasMessage("PRICING_STRATEGY_NOT_CONFIGURED");
  }

  @Test
  void registry_fails_fast_when_two_implementations_share_a_key() {
    PricingStrategy duplicate =
        new PricingStrategy() {
          @Override
          public String key() {
            return "DUPLICATA_MERCANTIL";
          }

          @Override
          public java.math.BigDecimal monthlySpread() {
            return java.math.BigDecimal.ZERO;
          }
        };

    assertThatThrownBy(
            () ->
                new PricingStrategyRegistry(
                    List.of(new DuplicataMercantilPricingStrategy(), duplicate)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate pricing strategy key");
  }

  @Test
  void registry_accepts_a_new_strategy_without_changes_to_registry_code() {
    PricingStrategy newStrategy =
        new PricingStrategy() {
          @Override
          public String key() {
            return "CONTRATO";
          }

          @Override
          public BigDecimal monthlySpread() {
            return new BigDecimal("0.030");
          }
        };

    var registry = new PricingStrategyRegistry(List.of(newStrategy));

    assertThat(registry.resolve("CONTRATO")).isSameAs(newStrategy);
  }

  @Test
  void registry_rejects_null_or_blank_strategy_keys_deterministically() {
    assertThatThrownBy(() -> new PricingStrategyRegistry(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("strategies");
    assertThatThrownBy(
            () -> new PricingStrategyRegistry(java.util.Arrays.asList((PricingStrategy) null)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("strategy");
    assertThatThrownBy(() -> new PricingStrategyRegistry(List.of(strategyWithKey(null))))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("strategy.key");
    assertThatThrownBy(() -> new PricingStrategyRegistry(List.of(strategyWithKey(" "))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("strategy.key must not be blank");
  }

  private static PricingStrategy strategyWithKey(String key) {
    return new PricingStrategy() {
      @Override
      public String key() {
        return key;
      }

      @Override
      public BigDecimal monthlySpread() {
        return BigDecimal.ZERO;
      }
    };
  }
}
