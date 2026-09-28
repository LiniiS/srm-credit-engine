package com.srm.creditengine.currency.domain.port;

import java.time.Instant;

/** Public read-only contract for the persisted FX snapshot applicable to a conversion. */
public interface ApplicableExchangeRateQuery {
  ApplicableExchangeRate find(String source, String target, Instant applicableAt);
}
