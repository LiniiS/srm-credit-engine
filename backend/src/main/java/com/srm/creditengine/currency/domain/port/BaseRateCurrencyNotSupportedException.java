package com.srm.creditengine.currency.domain.port;

/** Signals that a syntactically valid currency is absent from the supported catalog. */
public final class BaseRateCurrencyNotSupportedException extends RuntimeException {
  public static final String CODE = "CURRENCY_NOT_SUPPORTED";

  public BaseRateCurrencyNotSupportedException(String currencyCode) {
    super(CODE + ": " + currencyCode);
  }
}
