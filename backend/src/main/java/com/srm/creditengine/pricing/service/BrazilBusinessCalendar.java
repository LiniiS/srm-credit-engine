package com.srm.creditengine.pricing.service;

import com.srm.creditengine.pricing.domain.BusinessCalendar;
import com.srm.creditengine.pricing.domain.BusinessCalendarNotAvailableException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
final class BrazilBusinessCalendar implements BusinessCalendar {
  static final String SOURCE = "ANBIMA";
  static final LocalDate COVERAGE_START = LocalDate.of(2025, 1, 1);
  static final LocalDate COVERAGE_END = LocalDate.of(2030, 12, 31);
  static final LocalDate UPDATED_AT = LocalDate.of(2026, 9, 26);

  private final Set<LocalDate> holidays;

  BrazilBusinessCalendar() {
    holidays = loadHolidays();
  }

  @Override
  public void requireCovered(LocalDate date) {
    if (date == null || date.isBefore(COVERAGE_START) || date.isAfter(COVERAGE_END)) {
      throw new BusinessCalendarNotAvailableException();
    }
  }

  @Override
  public LocalDate nextOrSameBusinessDay(LocalDate date) {
    requireCovered(date);
    var candidate = date;
    while (!isBusinessDay(candidate)) {
      candidate = candidate.plusDays(1);
      requireCovered(candidate);
    }
    return candidate;
  }

  private boolean isBusinessDay(LocalDate date) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY
        && date.getDayOfWeek() != DayOfWeek.SUNDAY
        && !holidays.contains(date);
  }

  private Set<LocalDate> loadHolidays() {
    var resource = new ClassPathResource("calendars/anbima-brazil-2025-2030.csv");
    try (var reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      List<String> lines = reader.lines().toList();
      validateMetadata(lines);
      return lines.stream()
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(LocalDate::parse)
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException | RuntimeException exception) {
      throw new IllegalStateException("Unable to load the versioned ANBIMA calendar", exception);
    }
  }

  private void validateMetadata(List<String> lines) {
    requireMetadata(lines, "# source_name=", SOURCE);
    requireMetadata(lines, "# coverage=", COVERAGE_START + "/" + COVERAGE_END);
    requireMetadata(lines, "# retrieved_at=", UPDATED_AT.toString());
  }

  private void requireMetadata(List<String> lines, String prefix, String expected) {
    var actual =
        lines.stream()
            .filter(line -> line.startsWith(prefix))
            .findFirst()
            .map(line -> line.substring(prefix.length()));
    if (actual.isEmpty() || !actual.get().equals(expected)) {
      throw new IllegalStateException("Invalid versioned ANBIMA calendar metadata: " + prefix);
    }
  }
}
