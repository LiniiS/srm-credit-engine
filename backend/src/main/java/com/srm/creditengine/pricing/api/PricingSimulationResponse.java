package com.srm.creditengine.pricing.api;

import java.time.LocalDate;
import java.util.UUID;

public record PricingSimulationResponse(
    String faceValue,
    String currency,
    String paymentCurrencyCode,
    String receivableTypeCode,
    LocalDate calculationDate,
    LocalDate dueDate,
    LocalDate adjustedDueDate,
    long termDays,
    String termMonths,
    String baseRate,
    UUID baseRateId,
    String baseRateSource,
    String spread,
    String monthlyRate,
    String presentValue,
    String presentValueInPaymentCurrency,
    @org.springframework.lang.Nullable ExchangeRateSnapshot exchangeRate,
    String discount) {}

record ExchangeRateSnapshot(
    UUID id,
    String baseCurrencyCode,
    String quoteCurrencyCode,
    String rate,
    String source,
    java.time.Instant effectiveAt,
    java.time.Instant createdAt) {}
