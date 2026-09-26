package com.srm.creditengine.pricing.domain;

import java.util.Objects;
import java.util.UUID;

public record ReceivableType(
    UUID id,
    ReceivableTypeCode code,
    String name,
    String strategyKey,
    boolean active,
    long version) {
  public ReceivableType {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(code, "code");
    name = requireText(name, 100, "name");
    strategyKey = requireText(strategyKey, 64, "strategyKey");
    if (version < 0) {
      throw new IllegalArgumentException("version must be non-negative");
    }
  }

  private static String requireText(String value, int maxLength, String field) {
    Objects.requireNonNull(value, field);
    if (value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " must not be blank and must fit the catalog");
    }
    return value;
  }
}
