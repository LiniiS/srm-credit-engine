package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ResolvedPricingStrategy(
    UUID receivableTypeId,
    ReceivableTypeCode receivableTypeCode,
    String strategyKey,
    long receivableTypeVersion,
    BigDecimal monthlySpread) {
  public ResolvedPricingStrategy {
    Objects.requireNonNull(receivableTypeId, "receivableTypeId");
    Objects.requireNonNull(receivableTypeCode, "receivableTypeCode");
    Objects.requireNonNull(strategyKey, "strategyKey");
    Objects.requireNonNull(monthlySpread, "monthlySpread");
    if (monthlySpread.signum() < 0) {
      throw new IllegalArgumentException("monthlySpread must be non-negative");
    }
  }
}
