package com.srm.creditengine.currency.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Auditable monthly base-rate version returned to downstream domain consumers. */
public record BaseRate(
    UUID id, String currencyCode, BigDecimal rateMonthly, LocalDate effectiveFrom, String source) {

  public BaseRate {
    Objects.requireNonNull(id, "id");
    currencyCode = requireCurrencyCode(currencyCode);
    Objects.requireNonNull(rateMonthly, "rateMonthly");
    Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    if (rateMonthly.signum() < 0
        || rateMonthly.scale() > 12
        || rateMonthly.precision() - rateMonthly.scale() > 6) {
      throw new IllegalArgumentException("rateMonthly must be non-negative and fit NUMERIC(18,12)");
    }
    source = Objects.requireNonNull(source, "source");
    if (source.isBlank() || source.codePointCount(0, source.length()) > 64) {
      throw new IllegalArgumentException("source must not be blank and must have at most 64 chars");
    }
  }

  private static String requireCurrencyCode(String currencyCode) {
    var normalized = Objects.requireNonNull(currencyCode, "currencyCode");
    if (!normalized.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("currencyCode must contain three uppercase letters");
    }
    return normalized;
  }
}
