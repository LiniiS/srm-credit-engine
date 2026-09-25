package com.srm.creditengine.currency.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.ExchangeRate;
import com.srm.creditengine.currency.domain.port.ExchangeRateProvider;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import com.srm.creditengine.currency.domain.port.ProvidedExchangeRate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(ExchangeRateTransactionBoundaryTest.Config.class)
class ExchangeRateTransactionBoundaryTest {
  @Autowired ExchangeRateService service;
  @Autowired Probe probe;

  @Test
  void keeps_provider_outside_transaction_and_append_inside_short_transaction() {
    service.synchronize("USD", "BRL");

    assertThat(probe.providerTransactionActive).isFalse();
    assertThat(probe.appendTransactionActive).isTrue();
    assertThat(probe.appendCount).isOne();
  }

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  static class Config {
    @Bean
    Probe probe() {
      return new Probe();
    }

    @Bean
    ExchangeRateRepository repository(Probe probe) {
      return new ExchangeRateRepository() {
        @Override
        public ExchangeRate append(ExchangeRate exchangeRate) {
          probe.appendCount++;
          probe.appendTransactionActive =
              TransactionSynchronizationManager.isActualTransactionActive();
          return exchangeRate;
        }

        @Override
        public Optional<ExchangeRate> findLatest(
            CurrencyCode base, CurrencyCode quote, Instant applicableAt) {
          return Optional.empty();
        }

        @Override
        public boolean currencyExists(CurrencyCode currency) {
          return currency.value().equals("USD") || currency.value().equals("BRL");
        }
      };
    }

    @Bean
    ExchangeRateProvider provider(Probe probe) {
      return (base, quote) -> {
        probe.providerTransactionActive =
            TransactionSynchronizationManager.isActualTransactionActive();
        return new ProvidedExchangeRate(
            base,
            quote,
            new BigDecimal("5.12345678"),
            Instant.parse("2026-09-24T12:00:00Z"),
            "LOCAL_FX_MOCK");
      };
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-09-24T13:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    ExchangeRateWriter writer(ExchangeRateRepository repository) {
      return new ExchangeRateWriter(repository);
    }

    @Bean
    ExchangeRateService service(
        ExchangeRateRepository repository,
        Clock clock,
        ExchangeRateProvider provider,
        ExchangeRateWriter writer) {
      return new ExchangeRateService(repository, clock, provider, writer);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new TestTransactionManager();
    }
  }

  static final class Probe {
    boolean providerTransactionActive;
    boolean appendTransactionActive;
    int appendCount;
  }

  static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
