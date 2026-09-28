package com.srm.creditengine.currency.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApplicableExchangeRate(
    UUID id,
    String baseCurrencyCode,
    String quoteCurrencyCode,
    BigDecimal rate,
    String source,
    Instant effectiveAt,
    Instant createdAt) {}
