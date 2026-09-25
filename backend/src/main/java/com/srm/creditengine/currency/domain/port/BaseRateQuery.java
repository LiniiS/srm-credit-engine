package com.srm.creditengine.currency.domain.port;

import com.srm.creditengine.currency.domain.CurrencyCode;
import java.time.LocalDate;

/** Provides the auditable monthly base rate applicable to a currency and business date. */
public interface BaseRateQuery {
  BaseRate findApplicable(CurrencyCode currencyCode, LocalDate calculationDate);
}
