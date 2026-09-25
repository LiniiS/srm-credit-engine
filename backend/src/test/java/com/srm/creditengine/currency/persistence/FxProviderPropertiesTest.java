package com.srm.creditengine.currency.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FxProviderPropertiesTest {
  @Test
  void rejects_non_positive_durations_and_inconsistent_circuit_window() {
    assertThatThrownBy(() -> properties(Duration.ZERO, Duration.ofMillis(100), 4, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeout");
    assertThatThrownBy(() -> properties(Duration.ofSeconds(1), Duration.ZERO, 4, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("initialBackoff");
    assertThatThrownBy(() -> properties(Duration.ofSeconds(1), Duration.ofMillis(100), 4, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed");
  }

  private FxProviderProperties properties(
      Duration timeout, Duration backoff, int windowSize, int minimumCalls) {
    return new FxProviderProperties(
        "http://localhost:8090",
        timeout,
        3,
        backoff,
        windowSize,
        minimumCalls,
        50,
        Duration.ofSeconds(5),
        2);
  }
}
