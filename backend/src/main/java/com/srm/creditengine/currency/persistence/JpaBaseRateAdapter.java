package com.srm.creditengine.currency.persistence;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.BaseRate;
import com.srm.creditengine.currency.domain.port.BaseRateCurrencyNotSupportedException;
import com.srm.creditengine.currency.domain.port.BaseRateNotFoundException;
import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import com.srm.creditengine.currency.domain.port.BaseRateQueryException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JpaBaseRateAdapter implements BaseRateQuery {
  private final JpaBaseRateRepository repository;
  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;

  JpaBaseRateAdapter(
      JpaBaseRateRepository repository,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.entityManager = entityManager;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
  }

  @Override
  public BaseRate findApplicable(CurrencyCode currencyCode, LocalDate calculationDate) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(calculationDate, "calculationDate");
    try {
      return transactionTemplate.execute(status -> query(currencyCode, calculationDate));
    } catch (BaseRateCurrencyNotSupportedException | BaseRateNotFoundException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BaseRateQueryException(exception);
    }
  }

  private BaseRate query(CurrencyCode currencyCode, LocalDate calculationDate) {
    if (entityManager.find(CurrencyEntity.class, currencyCode.value()) == null) {
      throw new BaseRateCurrencyNotSupportedException(currencyCode.value());
    }
    return repository
        .findFirstByCurrencyCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            currencyCode.value(), calculationDate)
        .map(JpaBaseRateAdapter::toDomain)
        .orElseThrow(BaseRateNotFoundException::new);
  }

  private static BaseRate toDomain(BaseRateEntity entity) {
    return new BaseRate(
        entity.id, entity.currencyCode, entity.rateMonthly, entity.effectiveFrom, entity.source);
  }
}
