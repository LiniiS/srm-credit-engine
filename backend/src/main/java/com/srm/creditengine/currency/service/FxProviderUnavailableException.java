package com.srm.creditengine.currency.service;

public final class FxProviderUnavailableException extends RuntimeException {
  public FxProviderUnavailableException(Throwable cause) {
    super("FX provider unavailable", cause);
  }
}
