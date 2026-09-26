package com.srm.creditengine.pricing.persistence;

import com.srm.creditengine.pricing.domain.ReceivableType;
import com.srm.creditengine.pricing.domain.ReceivableTypeCode;
import com.srm.creditengine.pricing.domain.ReceivableTypeQueryException;
import com.srm.creditengine.pricing.domain.port.ReceivableTypeCatalog;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JpaReceivableTypeCatalog implements ReceivableTypeCatalog {
  private final JpaReceivableTypeRepository repository;
  private final TransactionTemplate transactionTemplate;

  JpaReceivableTypeCatalog(
      JpaReceivableTypeRepository repository, PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setReadOnly(true);
  }

  @Override
  public Optional<ReceivableType> findByCode(ReceivableTypeCode code) {
    try {
      return transactionTemplate.execute(
          status -> repository.findByCode(code.value()).map(JpaReceivableTypeCatalog::toDomain));
    } catch (RuntimeException exception) {
      throw new ReceivableTypeQueryException(exception);
    }
  }

  private static ReceivableType toDomain(ReceivableTypeEntity entity) {
    return new ReceivableType(
        entity.id,
        new ReceivableTypeCode(entity.code),
        entity.name,
        entity.strategyKey,
        entity.active,
        entity.version);
  }
}
