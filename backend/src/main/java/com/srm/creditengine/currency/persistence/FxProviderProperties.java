package com.srm.creditengine.currency.persistence;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("srm.fx-provider")
public record FxProviderProperties(
    @NotBlank String baseUrl,
    @NotNull Duration timeout,
    @Min(1) int maxAttempts,
    @NotNull Duration initialBackoff,
    @Min(1) int circuitBreakerWindowSize,
    @Min(1) int circuitBreakerMinimumCalls,
    @Min(1) @Max(100) int circuitBreakerFailureRateThreshold,
    @NotNull Duration circuitBreakerOpenDuration,
    @Min(1) int circuitBreakerHalfOpenCalls) {
  public FxProviderProperties {
    requirePositive(timeout, "timeout");
    requirePositive(initialBackoff, "initialBackoff");
    requirePositive(circuitBreakerOpenDuration, "circuitBreakerOpenDuration");
    if (circuitBreakerMinimumCalls > circuitBreakerWindowSize) {
      throw new IllegalArgumentException(
          "circuitBreakerMinimumCalls must not exceed circuitBreakerWindowSize");
    }
  }

  private static void requirePositive(Duration value, String name) {
    if (value != null && (value.isZero() || value.isNegative())) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
