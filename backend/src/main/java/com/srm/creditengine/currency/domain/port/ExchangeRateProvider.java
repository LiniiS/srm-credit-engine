package com.srm.creditengine.currency.domain.port;

import com.srm.creditengine.currency.domain.CurrencyCode;

public interface ExchangeRateProvider {
  ProvidedExchangeRate fetch(CurrencyCode base, CurrencyCode quote);
}
