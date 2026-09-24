package com.srm.creditengine.currency.service;

public final class CurrencyNotSupportedException extends RuntimeException {
  public CurrencyNotSupportedException(String currency) {
    super("Unsupported currency: " + currency);
  }
}
