package com.srm.creditengine.pricing.domain;

public final class PricingStrategyNotConfiguredException extends RuntimeException {
  public PricingStrategyNotConfiguredException() {
    super("PRICING_STRATEGY_NOT_CONFIGURED");
  }
}
