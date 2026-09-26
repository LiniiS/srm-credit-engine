package com.srm.creditengine.pricing.domain;

import java.time.LocalDate;

/** Adjusts economic dates using an explicitly versioned business calendar. */
public interface BusinessCalendar {
  void requireCovered(LocalDate date);

  LocalDate nextOrSameBusinessDay(LocalDate date);
}
