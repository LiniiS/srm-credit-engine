package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;

/** Decimal exponentiation boundary for financial calculations. */
public interface DecimalPower {
  BigDecimal pow(BigDecimal base, BigDecimal exponent);
}
