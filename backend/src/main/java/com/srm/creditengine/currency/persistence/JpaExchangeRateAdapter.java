package com.srm.creditengine.currency.persistence;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.ExchangeRate;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaExchangeRateAdapter implements ExchangeRateRepository {
  private final JpaExchangeRateRepository repository;
  private final EntityManager entityManager;

  JpaExchangeRateAdapter(JpaExchangeRateRepository repository, EntityManager entityManager) {
    this.repository = repository;
    this.entityManager = entityManager;
  }

  @Override
  public ExchangeRate append(ExchangeRate rate) {
    var entity = new ExchangeRateEntity();
    entity.id = rate.id();
    entity.baseCurrency = rate.baseCurrency().value();
    entity.quoteCurrency = rate.quoteCurrency().value();
    entity.rate = rate.rate();
    entity.source = rate.source();
    entity.effectiveAt = rate.effectiveAt();
    entity.createdAt = rate.createdAt();
    return toDomain(repository.save(entity));
  }

  @Override
  public Optional<ExchangeRate> findLatest(
      CurrencyCode base, CurrencyCode quote, Instant applicableAt) {
    return repository
        .findFirstByBaseCurrencyAndQuoteCurrencyAndEffectiveAtLessThanEqualOrderByEffectiveAtDescCreatedAtDescIdDesc(
            base.value(), quote.value(), applicableAt)
        .map(this::toDomain);
  }

  @Override
  public boolean currencyExists(CurrencyCode currency) {
    return entityManager.find(CurrencyEntity.class, currency.value()) != null;
  }

  private ExchangeRate toDomain(ExchangeRateEntity entity) {
    return new ExchangeRate(
        entity.id,
        new CurrencyCode(entity.baseCurrency),
        new CurrencyCode(entity.quoteCurrency),
        entity.rate,
        entity.source,
        entity.effectiveAt,
        entity.createdAt);
  }
}
