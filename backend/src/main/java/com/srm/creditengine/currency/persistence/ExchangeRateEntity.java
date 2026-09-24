package com.srm.creditengine.currency.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exchange_rate")
class ExchangeRateEntity {
  @Id UUID id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "base_currency", nullable = false, length = 3)
  String baseCurrency;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "quote_currency", nullable = false, length = 3)
  String quoteCurrency;

  @Column(nullable = false, precision = 18, scale = 8)
  BigDecimal rate;

  @Column(nullable = false, length = 100)
  String source;

  @Column(name = "effective_at", nullable = false)
  Instant effectiveAt;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  protected ExchangeRateEntity() {}
}
