package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

public final class PricingCalculation {
  private static final MathContext MC = MathContext.DECIMAL128;
  private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

  private final DecimalPower decimalPower;

  public PricingCalculation(DecimalPower decimalPower) {
    this.decimalPower = decimalPower;
  }

  public Values calculate(
      BigDecimal faceValue, long termDays, BigDecimal baseRate, BigDecimal spread, int minorUnits) {
    Objects.requireNonNull(faceValue, "faceValue");
    Objects.requireNonNull(baseRate, "baseRate");
    Objects.requireNonNull(spread, "spread");
    if (faceValue.signum() <= 0) {
      throw new IllegalArgumentException("faceValue must be positive");
    }
    if (termDays < 0) {
      throw new IllegalArgumentException("termDays must not be negative");
    }
    if (minorUnits < 0 || minorUnits > 6) {
      throw new IllegalArgumentException("minorUnits must be between 0 and 6");
    }
    var termMonths = BigDecimal.valueOf(termDays).divide(DAYS_PER_MONTH, MC);
    var monthlyRate = baseRate.add(spread, MC);
    var factor = decimalPower.pow(BigDecimal.ONE.add(monthlyRate, MC), termMonths);
    var rawPresentValue = faceValue.divide(factor, MC);
    var presentValue = rawPresentValue.setScale(minorUnits, RoundingMode.HALF_EVEN);
    var discount =
        faceValue.subtract(rawPresentValue, MC).setScale(minorUnits, RoundingMode.HALF_EVEN);
    return new Values(termMonths, monthlyRate, presentValue, discount);
  }

  public record Values(
      BigDecimal termMonths,
      BigDecimal monthlyRate,
      BigDecimal presentValue,
      BigDecimal discount) {}
}
