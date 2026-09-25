package com.srm.creditengine.currency.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.currency.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaseRateTest {
  private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final LocalDate EFFECTIVE_FROM = LocalDate.parse("2026-02-01");

  @Test
  void preserves_exact_fraction_and_traceable_source() {
    var rate = baseRate(new BigDecimal("0.010000000000"), " DEMO_SEED ");

    assertThat(rate.rateMonthly()).isEqualByComparingTo("0.010000000000");
    assertThat(rate.source()).isEqualTo(" DEMO_SEED ");
  }

  @Test
  void accepts_zero_and_scale_twelve() {
    assertThat(baseRate(new BigDecimal("0.000000000000"), "TEST_FIXTURE").rateMonthly())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void rejects_negative_rate() {
    assertThatThrownBy(() -> baseRate(new BigDecimal("-0.000000000001"), "TEST_FIXTURE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_scale_above_twelve_without_rounding() {
    assertThatThrownBy(() -> baseRate(new BigDecimal("0.0000000000001"), "TEST_FIXTURE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_value_that_does_not_fit_numeric_eighteen_twelve() {
    assertThatThrownBy(() -> baseRate(new BigDecimal("1000000.000000000000"), "TEST_FIXTURE"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void accepts_source_with_sixty_four_characters() {
    assertThat(baseRate(BigDecimal.ZERO, "A".repeat(64)).source()).hasSize(64);
  }

  @Test
  void rejects_null_blank_or_oversized_source() {
    assertThatThrownBy(() -> baseRate(BigDecimal.ZERO, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> baseRate(BigDecimal.ZERO, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> baseRate(BigDecimal.ZERO, "A".repeat(65)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_invalid_currency_and_null_required_fields() {
    assertThatThrownBy(
            () -> new BaseRate(ID, "brl", BigDecimal.ZERO, EFFECTIVE_FROM, "TEST_FIXTURE"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new BaseRate(null, "BRL", BigDecimal.ZERO, EFFECTIVE_FROM, "TEST_FIXTURE"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BaseRate(ID, "BRL", null, EFFECTIVE_FROM, "TEST_FIXTURE"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BaseRate(ID, "BRL", BigDecimal.ZERO, null, "TEST_FIXTURE"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void canonical_currency_value_object_rejects_malformed_codes() {
    assertThat(new CurrencyCode("BRL").value()).isEqualTo("BRL");
    assertThatThrownBy(() -> new CurrencyCode(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CurrencyCode("brl")).isInstanceOf(IllegalArgumentException.class);
  }

  private BaseRate baseRate(BigDecimal value, String source) {
    return new BaseRate(ID, "BRL", value, EFFECTIVE_FROM, source);
  }
}
