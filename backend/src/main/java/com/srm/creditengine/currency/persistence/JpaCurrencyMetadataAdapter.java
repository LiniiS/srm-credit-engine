package com.srm.creditengine.currency.persistence;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.CurrencyMetadata;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataNotFoundException;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQuery;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQueryException;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
class JpaCurrencyMetadataAdapter implements CurrencyMetadataQuery {
  private final EntityManager entityManager;

  JpaCurrencyMetadataAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public CurrencyMetadata find(String currencyCode) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    try {
      var code = new CurrencyCode(currencyCode);
      var entity = entityManager.find(CurrencyEntity.class, code.value());
      if (entity == null) {
        throw new CurrencyMetadataNotFoundException();
      }
      return new CurrencyMetadata(new CurrencyCode(entity.code), entity.minorUnits);
    } catch (CurrencyMetadataNotFoundException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new CurrencyMetadataQueryException(exception);
    }
  }
}
