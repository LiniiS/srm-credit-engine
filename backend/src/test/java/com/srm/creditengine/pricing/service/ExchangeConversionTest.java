package com.srm.creditengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.currency.domain.port.ApplicableExchangeRate;
import com.srm.creditengine.pricing.domain.ExchangeConversion;
import com.srm.creditengine.pricing.domain.PricingCalculation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExchangeConversionTest {
  private final PricingCalculation calculation = new PricingCalculation(new BigMathDecimalPower());
  private final ApplicableExchangeRate snapshot =
      new ApplicableExchangeRate(
          UUID.fromString("55555555-5555-4555-8555-555555555555"),
          "USD",
          "BRL",
          new BigDecimal("5.13000000"),
          "REFERENCE_CASE",
          Instant.parse("2026-01-05T11:50:00Z"),
          Instant.parse("2026-01-05T11:51:00Z"));

  @Test
  void converts_unrounded_usd_present_value_to_brl() {
    var values =
        calculation.calculate(
            new BigDecimal("2500.00"),
            45,
            new BigDecimal("0.005000000000"),
            new BigDecimal("0.025"),
            2);

    var converted =
        ExchangeConversion.convert(values.rawPresentValue(), "USD", "BRL", snapshot)
            .setScale(2, RoundingMode.HALF_EVEN);

    assertThat(values.presentValue()).isEqualByComparingTo("2391.58");
    assertThat(converted).isEqualByComparingTo("12268.78");
    assertThat(values.presentValue().multiply(snapshot.rate()).setScale(2, RoundingMode.HALF_EVEN))
        .isEqualByComparingTo("12268.81");
  }

  @Test
  void divides_brl_present_value_by_the_same_usd_brl_snapshot() {
    var values =
        calculation.calculate(
            new BigDecimal("1000.00"),
            31,
            new BigDecimal("0.010000000000"),
            new BigDecimal("0.015"),
            2);

    var converted =
        ExchangeConversion.convert(values.rawPresentValue(), "BRL", "USD", snapshot)
            .setScale(2, RoundingMode.HALF_EVEN);

    assertThat(values.presentValue()).isEqualByComparingTo("974.81");
    assertThat(converted).isEqualByComparingTo("190.02");
  }
}
