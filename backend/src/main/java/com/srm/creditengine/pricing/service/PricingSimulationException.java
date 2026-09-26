package com.srm.creditengine.pricing.service;

public final class PricingSimulationException extends RuntimeException {
  public enum Kind {
    BAD_REQUEST,
    NOT_FOUND,
    UNPROCESSABLE,
    INTERNAL
  }

  private final String code;
  private final String title;
  private final Kind kind;

  PricingSimulationException(String code, String title, Kind kind, Throwable cause) {
    super(code, cause);
    this.code = code;
    this.title = title;
    this.kind = kind;
  }

  public String code() {
    return code;
  }

  public String title() {
    return title;
  }

  public Kind kind() {
    return kind;
  }
}
