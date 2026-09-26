package com.srm.creditengine.currency.domain.port;

public final class CurrencyMetadataQueryException extends RuntimeException {
  public static final String CODE = "CURRENCY_METADATA_QUERY_FAILED";

  public CurrencyMetadataQueryException(Throwable cause) {
    super(CODE, cause);
  }
}
