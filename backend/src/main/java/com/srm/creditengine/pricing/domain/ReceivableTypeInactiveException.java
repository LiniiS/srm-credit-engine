package com.srm.creditengine.pricing.domain;

public final class ReceivableTypeInactiveException extends RuntimeException {
  public ReceivableTypeInactiveException() {
    super("RECEIVABLE_TYPE_INACTIVE");
  }
}
