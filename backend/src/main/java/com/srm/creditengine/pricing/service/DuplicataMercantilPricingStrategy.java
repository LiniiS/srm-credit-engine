package com.srm.creditengine.pricing.service;

import com.srm.creditengine.pricing.domain.PricingStrategy;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public final class DuplicataMercantilPricingStrategy implements PricingStrategy {
  private static final BigDecimal MONTHLY_SPREAD = new BigDecimal("0.015");

  @Override
  public String key() {
    return "DUPLICATA_MERCANTIL";
  }

  @Override
  public BigDecimal monthlySpread() {
    return MONTHLY_SPREAD;
  }
}
