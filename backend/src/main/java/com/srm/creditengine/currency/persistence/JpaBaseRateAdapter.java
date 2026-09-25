package com.srm.creditengine.currency.persistence;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.BaseRate;
import com.srm.creditengine.currency.domain.port.BaseRateCurrencyNotSupportedException;
import com.srm.creditengine.currency.domain.port.BaseRateNotFoundException;
import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import com.srm.creditengine.currency.domain.port.BaseRateQueryException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaBaseRateAdapter implements BaseRateQuery {
  private final JpaBaseRateRepository repository;
  private final EntityManager entityManager;

  JpaBaseRateAdapter(JpaBaseRateRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional(readOnly = true)
  public BaseRate findApplicable(CurrencyCode currencyCode, LocalDate calculationDate) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(calculationDate, "calculationDate");
    try {
      if (entityManager.find(CurrencyEntity.class, currencyCode.value()) == null) {
        throw new BaseRateCurrencyNotSupportedException(currencyCode.value());
      }
      return repository
          .findFirstByCurrencyCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
              currencyCode.value(), calculationDate)
          .map(JpaBaseRateAdapter::toDomain)
          .orElseThrow(BaseRateNotFoundException::new);
    } catch (DataAccessException | PersistenceException exception) {
      throw new BaseRateQueryException(exception);
    }
  }

  private static BaseRate toDomain(BaseRateEntity entity) {
    return new BaseRate(
        entity.id, entity.currencyCode, entity.rateMonthly, entity.effectiveFrom, entity.source);
  }
}
