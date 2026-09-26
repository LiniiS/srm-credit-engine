package com.srm.creditengine.pricing.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricingSimulationCommand(
    BigDecimal faceValue,
    String currency,
    String receivableTypeCode,
    LocalDate calculationDate,
    LocalDate dueDate) {}
