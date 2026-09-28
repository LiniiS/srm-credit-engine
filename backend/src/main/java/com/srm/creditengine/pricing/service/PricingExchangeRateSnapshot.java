package com.srm.creditengine.pricing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingExchangeRateSnapshot(
    UUID id,
    String baseCurrencyCode,
    String quoteCurrencyCode,
    BigDecimal rate,
    String source,
    Instant effectiveAt,
    Instant createdAt) {}
