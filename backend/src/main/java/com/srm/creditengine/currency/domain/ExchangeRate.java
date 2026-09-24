package com.srm.creditengine.currency.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExchangeRate(
    UUID id,
    CurrencyCode baseCurrency,
    CurrencyCode quoteCurrency,
    BigDecimal rate,
    String source,
    Instant effectiveAt,
    Instant createdAt) {

  public ExchangeRate {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(baseCurrency, "baseCurrency");
    Objects.requireNonNull(quoteCurrency, "quoteCurrency");
    Objects.requireNonNull(rate, "rate");
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    Objects.requireNonNull(createdAt, "createdAt");
    if (baseCurrency.equals(quoteCurrency)) {
      throw new IllegalArgumentException("currencies must be different");
    }
    if (rate.signum() <= 0 || rate.scale() > 8 || rate.precision() - rate.scale() > 10) {
      throw new IllegalArgumentException("rate must be positive and fit NUMERIC(18,8)");
    }
    source = Objects.requireNonNull(source, "source").trim();
    if (source.isEmpty() || source.length() > 100) {
      throw new IllegalArgumentException(
          "source must not be blank and must have at most 100 chars");
    }
  }
}
