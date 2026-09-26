package com.srm.creditengine.currency.domain.port;

public final class CurrencyMetadataNotFoundException extends RuntimeException {
  public static final String CODE = "CURRENCY_NOT_SUPPORTED";

  public CurrencyMetadataNotFoundException() {
    super(CODE);
  }
}
