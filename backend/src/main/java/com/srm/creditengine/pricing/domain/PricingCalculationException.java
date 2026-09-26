package com.srm.creditengine.pricing.domain;

public final class PricingCalculationException extends RuntimeException {
  public static final String CODE = "PRICING_CALCULATION_FAILED";

  public PricingCalculationException(Throwable cause) {
    super(CODE, cause);
  }
}
