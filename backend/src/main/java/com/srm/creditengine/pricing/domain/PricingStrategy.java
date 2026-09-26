package com.srm.creditengine.pricing.domain;

import java.math.BigDecimal;

/** Supplies the monthly risk spread for one stable catalog strategy key. */
public interface PricingStrategy {
  String key();

  BigDecimal monthlySpread();
}
