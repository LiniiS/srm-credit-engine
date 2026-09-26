package com.srm.creditengine.pricing.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaReceivableTypeRepository extends JpaRepository<ReceivableTypeEntity, UUID> {
  Optional<ReceivableTypeEntity> findByCode(String code);
}
