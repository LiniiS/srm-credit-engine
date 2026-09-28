package com.srm.creditengine.currency.domain.port;

public final class ExchangeRateExpiredException extends RuntimeException {
  public ExchangeRateExpiredException() {
    super("EXCHANGE_RATE_EXPIRED");
  }
}
