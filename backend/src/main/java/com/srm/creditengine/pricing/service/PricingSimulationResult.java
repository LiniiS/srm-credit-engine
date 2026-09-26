package com.srm.creditengine.pricing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PricingSimulationResult(
    BigDecimal faceValue,
    String currency,
    String receivableTypeCode,
    LocalDate calculationDate,
    LocalDate dueDate,
    LocalDate adjustedDueDate,
    long termDays,
    BigDecimal termMonths,
    BigDecimal baseRate,
    UUID baseRateId,
    String baseRateSource,
    BigDecimal spread,
    BigDecimal monthlyRate,
    BigDecimal presentValue,
    BigDecimal discount) {}
