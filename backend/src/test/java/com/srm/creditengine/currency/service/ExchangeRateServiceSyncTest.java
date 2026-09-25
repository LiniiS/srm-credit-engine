package com.srm.creditengine.currency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.ExchangeRate;
import com.srm.creditengine.currency.domain.port.ExchangeRateProvider;
import com.srm.creditengine.currency.domain.port.ExchangeRateProviderException;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import com.srm.creditengine.currency.domain.port.ProvidedExchangeRate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ExchangeRateServiceSyncTest {
  @Test
  void calls_provider_without_transaction_and_appends_exactly_once_after_validation() {
    var repository = new RecordingRepository();
    ExchangeRateProvider provider =
        (base, quote) -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          return new ProvidedExchangeRate(
              base,
              quote,
              new BigDecimal("5.12345678"),
              Instant.parse("2026-09-24T12:00:00Z"),
              "LOCAL_FX_MOCK");
        };
    var writer = new ExchangeRateWriter(repository);
    var clock = Clock.fixed(Instant.parse("2026-09-24T13:00:00Z"), ZoneOffset.UTC);
    var service = new ExchangeRateService(repository, clock, provider, writer);

    var result = service.synchronize("USD", "BRL");

    assertThat(repository.appendCount).isOne();
    assertThat(result.rate()).isEqualByComparingTo("5.12345678");
    assertThat(result.createdAt()).isEqualTo(clock.instant());
  }

  @Test
  void validates_catalog_before_provider_and_never_writes() {
    var repository = new RecordingRepository();
    var providerCalls = new AtomicInteger();
    ExchangeRateProvider provider =
        (base, quote) -> {
          providerCalls.incrementAndGet();
          throw new AssertionError("provider must not be called");
        };
    var service =
        new ExchangeRateService(
            repository,
            Clock.fixed(Instant.parse("2026-09-24T13:00:00Z"), ZoneOffset.UTC),
            provider,
            new ExchangeRateWriter(repository));

    assertThatThrownBy(() -> service.synchronize("EUR", "BRL"))
        .isInstanceOf(CurrencyNotSupportedException.class);
    assertThatThrownBy(() -> service.synchronize("USD", "USD"))
        .isInstanceOf(InvalidExchangeRateException.class);
    assertThat(providerCalls).hasValue(0);
    assertThat(repository.appendCount).isZero();
  }

  @Test
  void persistence_failure_does_not_repeat_provider_or_create_partial_history() {
    var repository = new RecordingRepository();
    repository.failAppend = true;
    var providerCalls = new AtomicInteger();
    ExchangeRateProvider provider =
        (base, quote) -> {
          providerCalls.incrementAndGet();
          return new ProvidedExchangeRate(
              base,
              quote,
              new BigDecimal("5.12345678"),
              Instant.parse("2026-09-24T12:00:00Z"),
              "LOCAL_FX_MOCK");
        };
    var service =
        new ExchangeRateService(
            repository,
            Clock.fixed(Instant.parse("2026-09-24T13:00:00Z"), ZoneOffset.UTC),
            provider,
            new ExchangeRateWriter(repository));

    assertThatThrownBy(() -> service.synchronize("USD", "BRL"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(providerCalls).hasValue(1);
    assertThat(repository.appendCount).isOne();
    assertThat(repository.persistedCount).isZero();
  }

  @Test
  void provider_failure_never_reaches_the_append_only_writer() {
    var repository = new RecordingRepository();
    ExchangeRateProvider provider =
        (base, quote) -> {
          throw new ExchangeRateProviderException("invalid provider response");
        };
    var service =
        new ExchangeRateService(
            repository,
            Clock.fixed(Instant.parse("2026-09-24T13:00:00Z"), ZoneOffset.UTC),
            provider,
            new ExchangeRateWriter(repository));

    assertThatThrownBy(() -> service.synchronize("USD", "BRL"))
        .isInstanceOf(FxProviderUnavailableException.class);
    assertThat(repository.appendCount).isZero();
    assertThat(repository.persistedCount).isZero();
  }

  private static final class RecordingRepository implements ExchangeRateRepository {
    int appendCount;
    int persistedCount;
    boolean failAppend;

    @Override
    public ExchangeRate append(ExchangeRate exchangeRate) {
      appendCount++;
      if (failAppend) {
        throw new IllegalStateException("simulated persistence failure");
      }
      persistedCount++;
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
  }
}
