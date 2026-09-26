package com.srm.creditengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQuery;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQueryException;
import com.srm.creditengine.pricing.domain.BusinessCalendar;
import com.srm.creditengine.pricing.domain.DecimalPower;
import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import com.srm.creditengine.pricing.domain.port.ReceivableTypePricingResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingSimulationServiceTest {
  private final ReceivableTypePricingResolver resolver = mock(ReceivableTypePricingResolver.class);
  private final BaseRateQuery baseRateQuery = mock(BaseRateQuery.class);
  private final CurrencyMetadataQuery metadataQuery = mock(CurrencyMetadataQuery.class);
  private final BusinessCalendar calendar = mock(BusinessCalendar.class);
  private final DecimalPower power = mock(DecimalPower.class);
  private final PricingSimulationService service =
      new PricingSimulationService(
          resolver, baseRateQuery, metadataQuery, calendar, power, new SimpleMeterRegistry());

  @BeforeEach
  void support_valid_calendar_dates() {
    when(calendar.nextOrSameBusinessDay(any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void contains_currency_infrastructure_failure_as_safe_internal_error() {
    when(metadataQuery.find("BRL"))
        .thenThrow(new CurrencyMetadataQueryException(new IllegalStateException("database")));

    assertThatThrownBy(() -> service.simulate(command()))
        .isInstanceOfSatisfying(
            PricingSimulationException.class,
            exception -> {
              org.assertj.core.api.Assertions.assertThat(exception.code())
                  .isEqualTo("PRICING_CALCULATION_FAILED");
              org.assertj.core.api.Assertions.assertThat(exception.kind())
                  .isEqualTo(PricingSimulationException.Kind.INTERNAL);
            });
  }

  @Test
  void strategy_not_configured_is_an_internal_inconsistency() {
    when(metadataQuery.find("BRL"))
        .thenReturn(
            new com.srm.creditengine.currency.domain.port.CurrencyMetadata(
                new com.srm.creditengine.currency.domain.CurrencyCode("BRL"), 2));
    when(resolver.resolve("DUPLICATA_MERCANTIL"))
        .thenThrow(new PricingStrategyNotConfiguredException());

    assertThatThrownBy(() -> service.simulate(command()))
        .isInstanceOfSatisfying(
            PricingSimulationException.class,
            exception -> {
              org.assertj.core.api.Assertions.assertThat(exception.code())
                  .isEqualTo("PRICING_STRATEGY_NOT_CONFIGURED");
              org.assertj.core.api.Assertions.assertThat(exception.kind())
                  .isEqualTo(PricingSimulationException.Kind.INTERNAL);
            });
  }

  private PricingSimulationCommand command() {
    return new PricingSimulationCommand(
        new BigDecimal("1000.00"),
        "BRL",
        "DUPLICATA_MERCANTIL",
        LocalDate.of(2026, 1, 2),
        LocalDate.of(2026, 2, 2));
  }
}
