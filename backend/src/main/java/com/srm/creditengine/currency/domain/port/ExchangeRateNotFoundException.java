package com.srm.creditengine.currency.domain.port;

public final class ExchangeRateNotFoundException extends RuntimeException {
  public ExchangeRateNotFoundException() {
    super("EXCHANGE_RATE_NOT_FOUND");
  }
}
