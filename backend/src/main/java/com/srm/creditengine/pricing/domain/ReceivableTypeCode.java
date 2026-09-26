package com.srm.creditengine.pricing.domain;

import java.util.Objects;

public record ReceivableTypeCode(String value) {
  public ReceivableTypeCode {
    Objects.requireNonNull(value, "value");
    if (!value.matches("[A-Z][A-Z0-9_]{0,63}")) {
      throw new IllegalArgumentException(
          "receivable type code must contain only uppercase letters, digits or underscores");
    }
  }
}
