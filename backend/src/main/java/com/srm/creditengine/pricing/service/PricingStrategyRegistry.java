package com.srm.creditengine.pricing.service;

import com.srm.creditengine.pricing.domain.PricingStrategy;
import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class PricingStrategyRegistry {
  private final Map<String, PricingStrategy> strategies;

  public PricingStrategyRegistry(List<PricingStrategy> strategies) {
    Objects.requireNonNull(strategies, "strategies");
    var indexed = new HashMap<String, PricingStrategy>();
    for (var strategy : strategies) {
      Objects.requireNonNull(strategy, "strategy");
      var strategyKey = Objects.requireNonNull(strategy.key(), "strategy.key");
      if (strategyKey.isBlank()) {
        throw new IllegalArgumentException("strategy.key must not be blank");
      }
      var previous = indexed.putIfAbsent(strategyKey, strategy);
      if (previous != null) {
        throw new IllegalStateException("Duplicate pricing strategy key: " + strategyKey);
      }
    }
    this.strategies = Map.copyOf(indexed);
  }

  PricingStrategy resolve(String strategyKey) {
    var strategy = strategies.get(strategyKey);
    if (strategy == null) {
      throw new PricingStrategyNotConfiguredException();
    }
    return strategy;
  }
}
