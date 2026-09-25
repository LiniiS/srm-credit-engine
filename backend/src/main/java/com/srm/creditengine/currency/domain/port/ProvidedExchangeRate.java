package com.srm.creditengine.currency.domain.port;

import com.srm.creditengine.currency.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;

public record ProvidedExchangeRate(
    CurrencyCode baseCurrency,
    CurrencyCode quoteCurrency,
    BigDecimal rate,
    Instant effectiveAt,
    String source) {}
