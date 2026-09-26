package com.srm.creditengine.pricing.api;

import java.time.LocalDate;
import java.util.UUID;

public record PricingSimulationResponse(
    String faceValue,
    String currency,
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
    String discount) {}
