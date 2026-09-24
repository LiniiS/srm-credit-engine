package com.srm.creditengine.currency.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "currency")
class CurrencyEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  String code;

  String name;

  @Column(name = "minor_units")
  short minorUnits;

  protected CurrencyEntity() {}
}
