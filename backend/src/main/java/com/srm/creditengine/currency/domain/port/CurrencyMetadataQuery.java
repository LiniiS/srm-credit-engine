package com.srm.creditengine.currency.domain.port;

/** Internal cross-module port exposing only calculation-safe currency metadata. */
public interface CurrencyMetadataQuery {
  CurrencyMetadata find(String currencyCode);
}
