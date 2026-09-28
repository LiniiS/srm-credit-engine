package com.srm.creditengine.currency.domain.port;

public final class ExchangeRateQueryException extends RuntimeException {
  public ExchangeRateQueryException(Throwable cause) {
    super("EXCHANGE_RATE_QUERY_FAILED", cause);
  }
}
