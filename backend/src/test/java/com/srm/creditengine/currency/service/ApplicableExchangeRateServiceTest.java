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
  void uses_valid_reverse_when_direct_rate_is_expired() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(20));
    var expiredDirect =
        rate(
            "66666666-6666-4666-8666-666666666666",
            BRL,
            USD,
            new BigDecimal("0.19000000"),
            EFFECTIVE_AT,
            EFFECTIVE_AT.plusSeconds(1));
    var validReverse =
        rate(
            "55555555-5555-4555-8555-555555555555",
            USD,
            BRL,
            new BigDecimal("5.13000000"),
            now.minus(Duration.ofMinutes(10)),
            now.minus(Duration.ofMinutes(9)));
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.of(expiredDirect));
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.of(validReverse));

    var selected = service.find("BRL", "USD", now);

    assertThat(selected.id()).isEqualTo(validReverse.id());
    assertThat(selected.baseCurrencyCode()).isEqualTo("USD");
    assertThat(selected.quoteCurrencyCode()).isEqualTo("BRL");
  }

  @Test
  void prefers_valid_direct_when_both_orientations_are_valid() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(10));
    var direct =
        rate(
            "66666666-6666-4666-8666-666666666666",
            BRL,
            USD,
            new BigDecimal("0.20000000"),
            now.minus(Duration.ofMinutes(5)),
            now.minus(Duration.ofMinutes(4)));
    var reverse = rate(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1));
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.of(direct));
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.of(reverse));

    var selected = service.find("BRL", "USD", now);

    assertThat(selected.id()).isEqualTo(direct.id());
    assertThat(selected.baseCurrencyCode()).isEqualTo("BRL");
    assertThat(selected.quoteCurrencyCode()).isEqualTo("USD");
  }

  @Test
  void reports_expired_when_direct_is_expired_and_reverse_is_absent() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(16));
    when(repository.findLatest(USD, BRL, now))
        .thenReturn(Optional.of(rate(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1))));
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.find("USD", "BRL", now))
        .isInstanceOf(ExchangeRateExpiredException.class);
  }

  @Test
  void reports_expired_when_both_orientations_are_expired() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(20));
    var direct = rate(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1));
    var reverse =
        rate(
            "66666666-6666-4666-8666-666666666666",
            BRL,
            USD,
            new BigDecimal("0.19000000"),
            EFFECTIVE_AT.minusSeconds(1),
            EFFECTIVE_AT);
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.of(direct));
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.of(reverse));

    assertThatThrownBy(() -> service.find("USD", "BRL", now))
        .isInstanceOf(ExchangeRateExpiredException.class);
  }

  @Test
  void ignores_future_direct_and_uses_valid_reverse() {
    var now = EFFECTIVE_AT.plus(Duration.ofMinutes(10));
    var futureDirect =
        rate(
            "66666666-6666-4666-8666-666666666666",
            BRL,
            USD,
            new BigDecimal("0.20000000"),
            now.plusSeconds(1),
            now);
    var validReverse = rate(EFFECTIVE_AT, EFFECTIVE_AT.plusSeconds(1));
    when(repository.findLatest(BRL, USD, now)).thenReturn(Optional.of(futureDirect));
    when(repository.findLatest(USD, BRL, now)).thenReturn(Optional.of(validReverse));

    var selected = service.find("BRL", "USD", now);

    assertThat(selected.id()).isEqualTo(validReverse.id());
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
    return rate(
        "55555555-5555-4555-8555-555555555555",
        USD,
        BRL,
        new BigDecimal("5.13000000"),
        effectiveAt,
        createdAt);
  }

  private ExchangeRate rate(
      String id,
      CurrencyCode base,
      CurrencyCode quote,
      BigDecimal value,
      Instant effectiveAt,
      Instant createdAt) {
    return new ExchangeRate(
        UUID.fromString(id), base, quote, value, "REFERENCE_CASE", effectiveAt, createdAt);
  }
}
