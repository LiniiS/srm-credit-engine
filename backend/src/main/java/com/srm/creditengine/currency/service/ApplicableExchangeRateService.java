package com.srm.creditengine.currency.service;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.ApplicableExchangeRate;
import com.srm.creditengine.currency.domain.port.ApplicableExchangeRateQuery;
import com.srm.creditengine.currency.domain.port.ExchangeRateExpiredException;
import com.srm.creditengine.currency.domain.port.ExchangeRateNotFoundException;
import com.srm.creditengine.currency.domain.port.ExchangeRateQueryException;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
final class ApplicableExchangeRateService implements ApplicableExchangeRateQuery {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicableExchangeRateService.class);

  private final ExchangeRateRepository repository;
  private final MeterRegistry meterRegistry;
  private final Duration maxAge;

  ApplicableExchangeRateService(
      ExchangeRateRepository repository,
      MeterRegistry meterRegistry,
      @Value("${srm.fx-rate.max-age:15m}") Duration maxAge) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
    if (maxAge.isZero() || maxAge.isNegative()) {
      throw new IllegalArgumentException("srm.fx-rate.max-age must be positive");
    }
    this.maxAge = maxAge;
  }

  @Override
  public ApplicableExchangeRate find(String sourceCode, String targetCode, Instant applicableAt) {
    var source = new CurrencyCode(sourceCode);
    var target = new CurrencyCode(targetCode);
    try {
      var snapshot =
          repository
              .findLatest(source, target, applicableAt)
              .or(() -> repository.findLatest(target, source, applicableAt))
              .orElseThrow(() -> notFound(source, target));
      var expiresAt = snapshot.effectiveAt().plus(maxAge);
      if (applicableAt.isAfter(expiresAt)) {
        var age = Duration.between(snapshot.effectiveAt(), applicableAt);
        meterRegistry.counter("srm.fx.conversion.failures", "reason", "expired").increment();
        LOGGER.info(
            "Applicable exchange rate rejected reason=expired pair={}/{} ageSeconds={} exchangeRateId={}",
            source,
            target,
            age.toSeconds(),
            snapshot.id());
        throw new ExchangeRateExpiredException();
      }
      return new ApplicableExchangeRate(
          snapshot.id(),
          snapshot.baseCurrency().value(),
          snapshot.quoteCurrency().value(),
          snapshot.rate(),
          snapshot.source(),
          snapshot.effectiveAt(),
          snapshot.createdAt());
    } catch (ExchangeRateNotFoundException | ExchangeRateExpiredException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new ExchangeRateQueryException(exception);
    }
  }

  private ExchangeRateNotFoundException notFound(CurrencyCode source, CurrencyCode target) {
    meterRegistry.counter("srm.fx.conversion.failures", "reason", "not_found").increment();
    LOGGER.info("Applicable exchange rate rejected reason=not_found pair={}/{}", source, target);
    return new ExchangeRateNotFoundException();
  }
}
