package com.srm.creditengine.currency.domain.port;

public final class ExchangeRateProviderException extends RuntimeException {
  public ExchangeRateProviderException(String message) {
    super(message);
  }

  public ExchangeRateProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
