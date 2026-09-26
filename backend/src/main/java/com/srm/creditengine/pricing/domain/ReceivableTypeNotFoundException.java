package com.srm.creditengine.pricing.domain;

public final class ReceivableTypeNotFoundException extends RuntimeException {
  public ReceivableTypeNotFoundException() {
    super("RECEIVABLE_TYPE_NOT_FOUND");
  }
}
