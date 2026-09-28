package com.srm.creditengine.pricing.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricingSimulationCommand(
    BigDecimal faceValue,
    String currency,
    String paymentCurrencyCode,
    String receivableTypeCode,
    LocalDate calculationDate,
    LocalDate dueDate) {
  public PricingSimulationCommand(
      BigDecimal faceValue,
      String currency,
      String receivableTypeCode,
      LocalDate calculationDate,
      LocalDate dueDate) {
    this(faceValue, currency, currency, receivableTypeCode, calculationDate, dueDate);
  }
}
