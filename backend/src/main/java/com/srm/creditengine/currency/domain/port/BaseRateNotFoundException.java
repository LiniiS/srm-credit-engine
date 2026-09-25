package com.srm.creditengine.currency.domain.port;

/** Signals that a catalogued currency has no base-rate version applicable on the requested date. */
public final class BaseRateNotFoundException extends RuntimeException {
  public static final String CODE = "BASE_RATE_NOT_FOUND";

  public BaseRateNotFoundException() {
    super(CODE);
  }
}
