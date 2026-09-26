package com.srm.creditengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.pricing.domain.PricingCalculation;
import com.srm.creditengine.pricing.domain.PricingCalculationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingCalculationTest {
  private final BigMathDecimalPower power = new BigMathDecimalPower();
  private final PricingCalculation calculation = new PricingCalculation(power);

  @Test
  void calculates_approved_31_day_case_with_exact_final_money() {
    var result =
        calculation.calculate(
            new BigDecimal("1000.00"),
            31,
            new BigDecimal("0.010000000000"),
            new BigDecimal("0.015"),
            2);

    assertThat(result.termMonths())
        .isEqualByComparingTo(
            new BigDecimal("31").divide(new BigDecimal("30"), java.math.MathContext.DECIMAL128));
    assertThat(result.presentValue()).isEqualByComparingTo("974.81");
    assertThat(result.discount()).isEqualByComparingTo("25.19");
  }

  @Test
  void calculates_approved_45_day_case_with_exact_final_money() {
    var result =
        calculation.calculate(
            new BigDecimal("2500.00"),
            45,
            new BigDecimal("0.005000000000"),
            new BigDecimal("0.025"),
            2);

    assertThat(result.presentValue()).isEqualByComparingTo("2391.58");
    assertThat(result.discount()).isEqualByComparingTo("108.42");
  }

  @Test
  void zero_term_returns_exact_nominal_and_zero_discount() {
    var result =
        calculation.calculate(
            new BigDecimal("1000.00"), 0, new BigDecimal("0.01"), new BigDecimal("0.015"), 2);

    assertThat(power.pow(new BigDecimal("1.025"), BigDecimal.ZERO)).isSameAs(BigDecimal.ONE);
    assertThat(result.presentValue()).isEqualByComparingTo("1000.00");
    assertThat(result.discount()).isEqualByComparingTo("0.00");
  }

  @Test
  void non_positive_power_base_is_a_safe_calculation_failure() {
    assertThatThrownBy(() -> power.pow(BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(PricingCalculationException.class)
        .hasMessage(PricingCalculationException.CODE);
  }
}
