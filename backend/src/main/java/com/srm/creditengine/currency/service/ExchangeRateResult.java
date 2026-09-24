package com.srm.creditengine.currency.service;

import com.srm.creditengine.currency.domain.ExchangeRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExchangeRateResult(
    UUID id,
    String baseCurrency,
    String quoteCurrency,
    BigDecimal rate,
    String source,
    Instant effectiveAt,
    Instant createdAt) {
  static ExchangeRateResult from(ExchangeRate value) {
    return new ExchangeRateResult(
        value.id(),
        value.baseCurrency().value(),
        value.quoteCurrency().value(),
        value.rate(),
        value.source(),
        value.effectiveAt(),
        value.createdAt());
  }
}
