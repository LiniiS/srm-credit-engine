package com.srm.creditengine.currency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.ExchangeRate;
import com.srm.creditengine.currency.domain.port.ExchangeRateExpiredException;
import com.srm.creditengine.currency.domain.port.ExchangeRateNotFoundException;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicableExchangeRateServiceTest {
  private static final CurrencyCode USD = new CurrencyCode("USD");
  private static final CurrencyCode BRL = new CurrencyCode("BRL");
  private static final Instant EFFECTIVE_AT = Instant.parse("2026-01-05T11:50:00Z");
  private final ExchangeRateRepository repository = mock(ExchangeRateRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final ApplicableExchangeRateService service =
      new ApplicableExchangeRateService(repository, meters, Duration.ofMinutes(15));

  @Test
  void accepts_rate_before_and_at_the_inclusive_expiration_boundary() {
    var rate = rate(EFFECTIVE_AT, Instant.parse("2026-01-05T11:51:00Z"));
    when(repository.findLatest(USD, BRL, EFFECTIVE_AT.plus(Duration.ofMinutes(14))))
        .thenReturn(Optional.of(rate));
    when(repository.findLatest(USD, BRL, EFFECTIVE_AT.plus(Duration.ofMinutes(15))))
        .thenReturn(Optional.of(rate));

    assertThat(service.find("USD", "BRL", EFFECTIVE_AT.plus(Duration.ofMinutes(14))).id())
        .isEqualTo(rate.id());
    assertThat(service.find("USD", "BRL", EFFECTIVE_AT.plus(Duration.ofMinutes(15))).id())
        .isEqualTo(rate.id());
  }

  @Test
  void rejects_after_boundary_using_effective_at_not_recent_created_at() {
    var rate = rate(EFFECTIVE_AT, EFFECTIVE_AT.plus(Duration.ofMinutes(16)));
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(15)).plusNanos(1);
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.of(rate));

    assertThatThrownBy(() -> service.find("USD", "BRL", now))
        .isInstanceOf(ExchangeRateExpiredException.class);
    assertThat(meters.counter("srm.fx.conversion.failures", "reason", "expired").count())
        .isEqualTo(1);
  }

  @Test
  void reuses_persisted_usd_brl_snapshot_for_brl_to_usd() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(10));
    var rate = rate(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1));
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.empty());
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.of(rate));

    assertThat(service.find("BRL", "USD", now).id()).isEqualTo(rate.id());
    verify(repository).findLatest(BRL, USD, now);
    verify(repository).findLatest(USD, BRL, now);
  }

  @Test
  void reports_not_found_when_neither_orientation_is_applicable() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(10));
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.empty());
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.find("USD", "BRL", now))
        .isInstanceOf(ExchangeRateNotFoundException.class);
    assertThat(meters.counter("srm.fx.conversion.failures", "reason", "not_found").count())
        .isEqualTo(1);
  }

  @Test
  void rejects_non_positive_validity_configuration() {
    assertThatThrownBy(() -> new ApplicableExchangeRateService(repository, meters, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("srm.fx-rate.max-age must be positive");
    assertThatThrownBy(
            () -> new ApplicableExchangeRateService(repository, meters, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("srm.fx-rate.max-age must be positive");
  }

  @Test
  void applies_a_non_default_validity_window() {
    var oneMinuteService =
        new ApplicableExchangeRateService(repository, meters, Duration.ofMinutes(1));
    var rate = rate(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(30));
    when(repository.findLatest(USD, BRL, EFFECTIVE_AT.plusSeconds(60)))
        .thenReturn(Optional.of(rate));
    when(repository.findLatest(USD, BRL, EFFECTIVE_AT.plusSeconds(61)))
        .thenReturn(Optional.of(rate));

    assertThat(oneMinuteService.find("USD", "BRL", EFFECTIVE_AT.plusSeconds(60)).id())
        .isEqualTo(rate.id());
    assertThatThrownBy(() -> oneMinuteService.find("USD", "BRL", EFFECTIVE_AT.plusSeconds(61)))
        .isInstanceOf(ExchangeRateExpiredException.class);
  }

  private ExchangeRate rate(Instant effectiveAt, Instant createdAt) {
    return new ExchangeRate(
        UUID.fromString("55555555-5555-4555-8555-555555555555"),
        USD,
        BRL,
        new BigDecimal("5.13000000"),
        "REFERENCE_CASE",
        effectiveAt,
        createdAt);
  }
}
