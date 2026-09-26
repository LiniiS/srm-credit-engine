package com.srm.creditengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import com.srm.creditengine.pricing.domain.ReceivableType;
import com.srm.creditengine.pricing.domain.ReceivableTypeCode;
import com.srm.creditengine.pricing.domain.ReceivableTypeInactiveException;
import com.srm.creditengine.pricing.domain.ReceivableTypeNotFoundException;
import com.srm.creditengine.pricing.domain.ReceivableTypeQueryException;
import com.srm.creditengine.pricing.domain.port.ReceivableTypeCatalog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ReceivableTypePricingServiceTest {
  private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Test
  void resolution_preserves_catalog_identity_and_returns_strategy_spread() {
    var service =
        serviceWith(
            type("DUPLICATA_MERCANTIL", true), initialRegistry(), new SimpleMeterRegistry());

    var result = service.resolve("DUPLICATA_MERCANTIL");

    assertThat(result.receivableTypeId()).isEqualTo(ID);
    assertThat(result.receivableTypeCode().value()).isEqualTo("DUPLICATA_MERCANTIL");
    assertThat(result.strategyKey()).isEqualTo("DUPLICATA_MERCANTIL");
    assertThat(result.receivableTypeVersion()).isZero();
    assertThat(result.monthlySpread()).isEqualByComparingTo("0.015");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"duplicata", "DUPLICATA-MERCANTIL", " DUPLICATA_MERCANTIL"})
  void malformed_types_fail_before_catalog_or_strategy_resolution(String invalidCode) {
    ReceivableTypeCatalog catalog =
        code -> {
          throw new AssertionError("catalog must not be queried for malformed code");
        };
    var service =
        new ReceivableTypePricingService(catalog, initialRegistry(), new SimpleMeterRegistry());

    assertThatThrownBy(() -> service.resolve(invalidCode))
        .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
  }

  @Test
  void missing_and_inactive_types_fail_before_strategy_resolution() {
    var registry = initialRegistry();
    var meterRegistry = new SimpleMeterRegistry();

    assertThatThrownBy(() -> serviceWith(null, registry, meterRegistry).resolve("CONTRATO"))
        .isInstanceOf(ReceivableTypeNotFoundException.class)
        .hasMessage("RECEIVABLE_TYPE_NOT_FOUND");
    assertThatThrownBy(
            () ->
                serviceWith(type("DUPLICATA_MERCANTIL", false), registry, meterRegistry)
                    .resolve("DUPLICATA_MERCANTIL"))
        .isInstanceOf(ReceivableTypeInactiveException.class)
        .hasMessage("RECEIVABLE_TYPE_INACTIVE");
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  @Test
  void inactive_type_never_executes_the_configured_strategy() {
    com.srm.creditengine.pricing.domain.PricingStrategy mustNotRun =
        new com.srm.creditengine.pricing.domain.PricingStrategy() {
          @Override
          public String key() {
            return "DUPLICATA_MERCANTIL";
          }

          @Override
          public java.math.BigDecimal monthlySpread() {
            throw new AssertionError("inactive type must not execute pricing strategy");
          }
        };
    var service =
        serviceWith(
            type("DUPLICATA_MERCANTIL", false),
            new PricingStrategyRegistry(List.of(mustNotRun)),
            new SimpleMeterRegistry());

    assertThatThrownBy(() -> service.resolve("DUPLICATA_MERCANTIL"))
        .isInstanceOf(ReceivableTypeInactiveException.class);
  }

  @Test
  void missing_strategy_logs_once_and_increments_only_the_low_cardinality_metric(
      CapturedOutput output) {
    var meterRegistry = new SimpleMeterRegistry();
    var service = serviceWith(type("NOT_DEPLOYED", true), initialRegistry(), meterRegistry);

    assertThatThrownBy(() -> service.resolve("DUPLICATA_MERCANTIL"))
        .isInstanceOf(PricingStrategyNotConfiguredException.class)
        .hasMessage("PRICING_STRATEGY_NOT_CONFIGURED");

    assertThat(output.getOut())
        .containsOnlyOnce("Pricing strategy is not configured")
        .contains("receivableTypeCode=DUPLICATA_MERCANTIL")
        .contains("strategyKey=NOT_DEPLOYED");
    var counter =
        meterRegistry
            .get("srm.pricing.strategy.resolution.failures")
            .tag("reason", "not_configured")
            .counter();
    assertThat(counter.count()).isEqualTo(1.0);
    assertThat(counter.getId().getTags()).extracting("key").containsExactly("reason");
  }

  @Test
  void telemetry_failure_does_not_replace_the_stable_strategy_error() {
    var meterRegistry = mock(io.micrometer.core.instrument.MeterRegistry.class);
    when(meterRegistry.counter(
            "srm.pricing.strategy.resolution.failures", "reason", "not_configured"))
        .thenThrow(new IllegalStateException("telemetry unavailable"));
    var service =
        new ReceivableTypePricingService(
            code -> Optional.of(type("NOT_DEPLOYED", true)), initialRegistry(), meterRegistry);

    assertThatThrownBy(() -> service.resolve("DUPLICATA_MERCANTIL"))
        .isInstanceOf(PricingStrategyNotConfiguredException.class)
        .hasMessage("PRICING_STRATEGY_NOT_CONFIGURED");
  }

  @Test
  void stable_catalog_failure_crosses_the_resolver_without_infrastructure_exceptions() {
    ReceivableTypeCatalog catalog =
        code -> {
          throw new ReceivableTypeQueryException(new IllegalStateException("database detail"));
        };
    var service =
        new ReceivableTypePricingService(catalog, initialRegistry(), new SimpleMeterRegistry());

    assertThatThrownBy(() -> service.resolve("DUPLICATA_MERCANTIL"))
        .isInstanceOf(ReceivableTypeQueryException.class)
        .hasMessage("RECEIVABLE_TYPE_QUERY_FAILED");
  }

  private static ReceivableTypePricingService serviceWith(
      ReceivableType type, PricingStrategyRegistry registry, SimpleMeterRegistry meterRegistry) {
    ReceivableTypeCatalog catalog = code -> Optional.ofNullable(type);
    return new ReceivableTypePricingService(catalog, registry, meterRegistry);
  }

  private static PricingStrategyRegistry initialRegistry() {
    return new PricingStrategyRegistry(
        List.of(new DuplicataMercantilPricingStrategy(), new ChequePreDatadoPricingStrategy()));
  }

  private static ReceivableType type(String strategyKey, boolean active) {
    return new ReceivableType(
        ID,
        new ReceivableTypeCode("DUPLICATA_MERCANTIL"),
        "Duplicata Mercantil",
        strategyKey,
        active,
        0);
  }
}
