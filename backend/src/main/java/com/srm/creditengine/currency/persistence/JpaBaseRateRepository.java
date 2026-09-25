package com.srm.creditengine.currency.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaBaseRateRepository extends JpaRepository<BaseRateEntity, UUID> {
  Optional<BaseRateEntity>
      findFirstByCurrencyCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
          String currencyCode, LocalDate calculationDate);
}
