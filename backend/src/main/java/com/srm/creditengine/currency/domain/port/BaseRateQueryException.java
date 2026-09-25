package com.srm.creditengine.currency.domain.port;

/** Stable domain-facing failure used when the base-rate store cannot answer a query. */
public final class BaseRateQueryException extends RuntimeException {
  public static final String CODE = "BASE_RATE_QUERY_FAILED";

  public BaseRateQueryException(Throwable cause) {
    super(CODE, cause);
  }
}
