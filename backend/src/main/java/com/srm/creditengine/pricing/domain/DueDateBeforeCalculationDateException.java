package com.srm.creditengine.pricing.domain;

public final class DueDateBeforeCalculationDateException extends RuntimeException {
  public static final String CODE = "DUE_DATE_BEFORE_CALCULATION_DATE";

  public DueDateBeforeCalculationDateException() {
    super(CODE);
  }
}
