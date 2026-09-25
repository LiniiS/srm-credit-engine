package com.srm.creditengine.currency.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "base_rate")
class BaseRateEntity {
  @Id UUID id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "currency_code", nullable = false, length = 3)
  String currencyCode;

  @Column(name = "rate_monthly", nullable = false, precision = 18, scale = 12)
  BigDecimal rateMonthly;

  @Column(name = "effective_from", nullable = false)
  LocalDate effectiveFrom;

  @Column(nullable = false, length = 64)
  String source;

  protected BaseRateEntity() {}
}
