package com.srm.creditengine.pricing.service;

import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import com.srm.creditengine.pricing.domain.ReceivableTypeCode;
import com.srm.creditengine.pricing.domain.ReceivableTypeInactiveException;
import com.srm.creditengine.pricing.domain.ReceivableTypeNotFoundException;
import com.srm.creditengine.pricing.domain.ResolvedPricingStrategy;
import com.srm.creditengine.pricing.domain.port.ReceivableTypeCatalog;
import com.srm.creditengine.pricing.domain.port.ReceivableTypePricingResolver;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
final class ReceivableTypePricingService implements ReceivableTypePricingResolver {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceivableTypePricingService.class);

  private final ReceivableTypeCatalog catalog;
  private final PricingStrategyRegistry registry;
  private final MeterRegistry meterRegistry;

  ReceivableTypePricingService(
      ReceivableTypeCatalog catalog,
      PricingStrategyRegistry registry,
      MeterRegistry meterRegistry) {
    this.catalog = catalog;
    this.registry = registry;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public ResolvedPricingStrategy resolve(String receivableTypeCode) {
    var code = new ReceivableTypeCode(receivableTypeCode);
    var type = catalog.findByCode(code).orElseThrow(ReceivableTypeNotFoundException::new);
    if (!type.active()) {
      throw new ReceivableTypeInactiveException();
    }
    try {
      var strategy = registry.resolve(type.strategyKey());
      return new ResolvedPricingStrategy(
          type.id(), type.code(), type.strategyKey(), type.version(), strategy.monthlySpread());
    } catch (PricingStrategyNotConfiguredException exception) {
      LOGGER.error(
          "Pricing strategy is not configured receivableTypeCode={} strategyKey={}",
          type.code().value(),
          type.strategyKey());
      incrementStrategyResolutionFailureMetric();
      throw exception;
    }
  }

  private void incrementStrategyResolutionFailureMetric() {
    try {
      meterRegistry
          .counter("srm.pricing.strategy.resolution.failures", "reason", "not_configured")
          .increment();
    } catch (RuntimeException telemetryFailure) {
      LOGGER.warn("Unable to record pricing strategy resolution metric", telemetryFailure);
    }
  }
}
