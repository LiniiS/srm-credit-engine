package com.srm.creditengine.currency.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaExchangeRateRepository extends JpaRepository<ExchangeRateEntity, UUID> {
  Optional<ExchangeRateEntity>
      findFirstByBaseCurrencyAndQuoteCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDescCreatedAtDescIdDesc(
          String baseCurrency, String quoteCurrency, Instant effectiveAt);
}
