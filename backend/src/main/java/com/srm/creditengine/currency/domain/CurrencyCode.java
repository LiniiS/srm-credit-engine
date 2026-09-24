package com.srm.creditengine.currency.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record CurrencyCode(String value) {
  private static final Pattern FORMAT = Pattern.compile("[A-Z]{3}");

  public CurrencyCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("currency must contain three uppercase letters");
    }
  }
}
