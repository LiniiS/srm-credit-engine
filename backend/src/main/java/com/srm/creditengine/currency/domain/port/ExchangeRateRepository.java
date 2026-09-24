package com.srm.creditengine.currency.domain.port;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.ExchangeRate;
import java.time.Instant;
import java.util.Optional;

public interface ExchangeRateRepository {
  ExchangeRate append(ExchangeRate exchangeRate);

  Optional<ExchangeRate> findLatest(CurrencyCode base, CurrencyCode quote, Instant applicableAt);

  boolean currencyExists(CurrencyCode currency);
}
