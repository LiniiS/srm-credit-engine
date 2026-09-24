package com.srm.creditengine.currency.service;

public final class ExchangeRateNotFoundException extends RuntimeException {
  public ExchangeRateNotFoundException() {
    super("No applicable exchange rate was found");
  }
}
