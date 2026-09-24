package com.srm.creditengine.currency.service;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.ExchangeRate;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateService {
  private final ExchangeRateRepository repository;
  private final Clock clock;

  public ExchangeRateService(ExchangeRateRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public ExchangeRateResult register(
      String base, String quote, BigDecimal rate, String source, Instant effectiveAt) {
    var baseCode = new CurrencyCode(base);
    var quoteCode = new CurrencyCode(quote);
    requireSupported(baseCode);
    requireSupported(quoteCode);
    return ExchangeRateResult.from(
        repository.append(
            new ExchangeRate(
                UUID.randomUUID(),
                baseCode,
                quoteCode,
                rate,
                source,
                effectiveAt.truncatedTo(ChronoUnit.MICROS),
                clock.instant().truncatedTo(ChronoUnit.MICROS))));
  }

  @Transactional(readOnly = true)
  public ExchangeRateResult latest(String base, String quote) {
    var baseCode = new CurrencyCode(base);
    var quoteCode = new CurrencyCode(quote);
    if (baseCode.equals(quoteCode)) {
      throw new IllegalArgumentException("currencies must be different");
    }
    requireSupported(baseCode);
    requireSupported(quoteCode);
    return repository
        .findLatest(baseCode, quoteCode, clock.instant())
        .map(ExchangeRateResult::from)
        .orElseThrow(ExchangeRateNotFoundException::new);
  }

  private void requireSupported(CurrencyCode currency) {
    if (!repository.currencyExists(currency)) {
      throw new CurrencyNotSupportedException(currency.value());
    }
  }
}
