package com.srm.creditengine.pricing.domain;

public final class ReceivableTypeQueryException extends RuntimeException {
  public ReceivableTypeQueryException(Throwable cause) {
    super("RECEIVABLE_TYPE_QUERY_FAILED", cause);
  }
}
