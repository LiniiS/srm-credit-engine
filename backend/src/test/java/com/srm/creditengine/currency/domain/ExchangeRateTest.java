package com.srm.creditengine.currency.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExchangeRateTest {
  private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

  @Test
  void rejects_invalid_currency_codes() {
    assertThatThrownBy(() -> new CurrencyCode("usd")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_equal_currencies() {
    assertThatThrownBy(
            () ->
                rate(
                    new CurrencyCode("USD"),
                    new CurrencyCode("USD"),
                    new BigDecimal("5.10000000"),
                    "MANUAL"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_non_positive_or_excessively_scaled_rates_and_blank_source() {
    var usd = new CurrencyCode("USD");
    var brl = new CurrencyCode("BRL");
    assertThatThrownBy(() -> rate(usd, brl, BigDecimal.ZERO, "MANUAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rate(usd, brl, new BigDecimal("-0.00000001"), "MANUAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rate(usd, brl, null, "MANUAL"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> rate(usd, brl, new BigDecimal("1.000000001"), "MANUAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> rate(usd, brl, BigDecimal.ONE, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ExchangeRate rate(
      CurrencyCode base, CurrencyCode quote, BigDecimal value, String source) {
    return new ExchangeRate(UUID.randomUUID(), base, quote, value, source, NOW, NOW);
  }
}
