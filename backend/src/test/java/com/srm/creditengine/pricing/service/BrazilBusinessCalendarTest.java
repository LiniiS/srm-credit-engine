package com.srm.creditengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.pricing.domain.BusinessCalendarNotAvailableException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BrazilBusinessCalendarTest {
  private final BrazilBusinessCalendar calendar = new BrazilBusinessCalendar();

  @Test
  void keeps_business_day_and_advances_weekend() {
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2026, 1, 2)))
        .isEqualTo(LocalDate.of(2026, 1, 2));
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2026, 1, 3)))
        .isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2026, 1, 4)))
        .isEqualTo(LocalDate.of(2026, 1, 5));
  }

  @Test
  void advances_carnival_corpus_christi_and_new_year_boundary() {
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2026, 2, 16)))
        .isEqualTo(LocalDate.of(2026, 2, 18));
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2026, 6, 4)))
        .isEqualTo(LocalDate.of(2026, 6, 5));
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2028, 12, 30)))
        .isEqualTo(LocalDate.of(2029, 1, 2));
  }

  @Test
  void rejects_dates_outside_versioned_coverage() {
    assertThatThrownBy(() -> calendar.nextOrSameBusinessDay(LocalDate.of(2024, 12, 31)))
        .isInstanceOf(BusinessCalendarNotAvailableException.class);
    assertThatThrownBy(() -> calendar.nextOrSameBusinessDay(LocalDate.of(2031, 1, 1)))
        .isInstanceOf(BusinessCalendarNotAvailableException.class);
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2025, 1, 1)))
        .isEqualTo(LocalDate.of(2025, 1, 2));
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2030, 12, 31)))
        .isEqualTo(LocalDate.of(2030, 12, 31));
  }
}
