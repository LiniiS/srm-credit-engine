package com.srm.creditengine.pricing.domain;

public final class BusinessCalendarNotAvailableException extends RuntimeException {
  public static final String CODE = "BUSINESS_CALENDAR_NOT_AVAILABLE";

  public BusinessCalendarNotAvailableException() {
    super(CODE);
  }
}
