package com.srm.creditengine.pricing.domain;

import com.srm.creditengine.currency.domain.port.ApplicableExchangeRate;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public final class ExchangeConversion {
  private ExchangeConversion() {}

  public static BigDecimal convert(
      BigDecimal amount, String source, String target, ApplicableExchangeRate snapshot) {
    Objects.requireNonNull(amount, "amount");
    if (snapshot.baseCurrencyCode().equals(source) && snapshot.quoteCurrencyCode().equals(target)) {
      return amount.multiply(snapshot.rate(), MathContext.DECIMAL128);
    }
    if (snapshot.quoteCurrencyCode().equals(source) && snapshot.baseCurrencyCode().equals(target)) {
      return amount.divide(snapshot.rate(), MathContext.DECIMAL128);
    }
    throw new IllegalArgumentException("exchange-rate snapshot does not match conversion");
  }
}
