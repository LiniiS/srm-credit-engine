package com.srm.creditengine.pricing.service;

import com.srm.creditengine.pricing.domain.PricingStrategy;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public final class ChequePreDatadoPricingStrategy implements PricingStrategy {
  private static final BigDecimal MONTHLY_SPREAD = new BigDecimal("0.025");

  @Override
  public String key() {
    return "CHEQUE_PRE_DATADO";
  }

  @Override
  public BigDecimal monthlySpread() {
    return MONTHLY_SPREAD;
  }
}
