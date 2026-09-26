package com.srm.creditengine.pricing.service;

import ch.obermuhlner.math.big.BigDecimalMath;
import com.srm.creditengine.pricing.domain.DecimalPower;
import com.srm.creditengine.pricing.domain.PricingCalculationException;
import java.math.BigDecimal;
import java.math.MathContext;
import org.springframework.stereotype.Component;

@Component
final class BigMathDecimalPower implements DecimalPower {
  @Override
  public BigDecimal pow(BigDecimal base, BigDecimal exponent) {
    if (base.signum() <= 0) {
      throw new PricingCalculationException(new ArithmeticException("power base must be positive"));
    }
    if (exponent.signum() == 0) {
      return BigDecimal.ONE;
    }
    try {
      return BigDecimalMath.pow(base, exponent, MathContext.DECIMAL128);
    } catch (RuntimeException exception) {
      throw new PricingCalculationException(exception);
    }
  }
}
