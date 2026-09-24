package com.srm.creditengine.currency.service;

public class InvalidExchangeRateException extends RuntimeException {
  private final String field;

  public InvalidExchangeRateException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
