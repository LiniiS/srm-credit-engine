package com.srm.creditengine.currency.domain.port;

import com.srm.creditengine.currency.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.Objects;

/** Currency metadata needed by financial calculations. */
public record CurrencyMetadata(CurrencyCode code, int minorUnits) {
  public CurrencyMetadata {
    Objects.requireNonNull(code, "code");
    if (minorUnits < 0 || minorUnits > 6) {
      throw new IllegalArgumentException("minorUnits must be between 0 and 6");
    }
  }

  public BaseRate findApplicableBaseRate(BaseRateQuery query, LocalDate calculationDate) {
    return query.findApplicable(code, calculationDate);
  }
}
