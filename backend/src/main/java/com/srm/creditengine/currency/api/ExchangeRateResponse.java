package com.srm.creditengine.currency.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.srm.creditengine.currency.service.ExchangeRateResult;
import java.time.Instant;
import java.util.UUID;

public record ExchangeRateResponse(
    UUID id,
    String baseCurrency,
    String quoteCurrency,
    @JsonProperty String rate,
    String source,
    Instant effectiveAt,
    Instant createdAt) {
  static ExchangeRateResponse from(ExchangeRateResult value) {
    return new ExchangeRateResponse(
        value.id(),
        value.baseCurrency(),
        value.quoteCurrency(),
        value.rate().setScale(8).toPlainString(),
        value.source(),
        value.effectiveAt(),
        value.createdAt());
  }
}
