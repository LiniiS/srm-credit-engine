package com.srm.creditengine.pricing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "receivable_type")
class ReceivableTypeEntity {
  @Id UUID id;

  @Column(nullable = false, unique = true, length = 64)
  String code;

  @Column(nullable = false, length = 100)
  String name;

  @Column(name = "strategy_key", nullable = false, length = 64)
  String strategyKey;

  @Column(nullable = false)
  boolean active;

  @Version
  @Column(nullable = false)
  long version;

  protected ReceivableTypeEntity() {}
}
