package com.srm.creditengine.pricing.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.ApplicableExchangeRateQuery;
import com.srm.creditengine.currency.domain.port.BaseRate;
import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import com.srm.creditengine.currency.domain.port.BaseRateQueryException;
import com.srm.creditengine.currency.domain.port.CurrencyMetadata;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQuery;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQueryException;
import com.srm.creditengine.currency.domain.port.ExchangeRateExpiredException;
import com.srm.creditengine.currency.domain.port.ExchangeRateNotFoundException;
import com.srm.creditengine.currency.domain.port.ExchangeRateQueryException;
import com.srm.creditengine.pricing.domain.BusinessCalendar;
import com.srm.creditengine.pricing.domain.DecimalPower;
import com.srm.creditengine.pricing.domain.PricingCalculationException;
import com.srm.creditengine.pricing.domain.PricingStrategyNotConfiguredException;
import com.srm.creditengine.pricing.domain.ReceivableTypeCode;
import com.srm.creditengine.pricing.domain.ReceivableTypeInactiveException;
import com.srm.creditengine.pricing.domain.ResolvedPricingStrategy;
import com.srm.creditengine.pricing.domain.port.ReceivableTypePricingResolver;
import com.srm.creditengine.pricing.service.PricingSimulationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PricingSimulationController.class)
@Import({
  PricingSimulationService.class,
  PricingSimulationExceptionHandler.class,
  PricingSimulationFailureHttpTest.MeterConfiguration.class
})
class PricingSimulationFailureHttpTest {
  private static final String REQUEST =
      """
      {"faceValue":"1000.00","currency":"BRL",
       "receivableTypeCode":"DUPLICATA_MERCANTIL",
       "calculationDate":"2026-01-02","dueDate":"2026-02-02"}
      """;
  private static final String FX_REQUEST =
      """
      {"faceValue":"1000.00","currency":"BRL","paymentCurrencyCode":"USD",
       "receivableTypeCode":"DUPLICATA_MERCANTIL",
       "calculationDate":"2026-01-02","dueDate":"2026-02-02"}
      """;

  @Autowired MockMvc mockMvc;
  @Autowired MeterRegistry meterRegistry;
  @MockBean ReceivableTypePricingResolver pricingResolver;
  @MockBean BaseRateQuery baseRateQuery;
  @MockBean CurrencyMetadataQuery currencyMetadataQuery;
  @MockBean ApplicableExchangeRateQuery exchangeRateQuery;
  @MockBean Clock clock;
  @MockBean BusinessCalendar businessCalendar;
  @MockBean DecimalPower decimalPower;

  @BeforeEach
  void valid_collaborators() {
    when(businessCalendar.nextOrSameBusinessDay(any(LocalDate.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(currencyMetadataQuery.find("BRL"))
        .thenReturn(new CurrencyMetadata(new CurrencyCode("BRL"), 2));
    when(currencyMetadataQuery.find("USD"))
        .thenReturn(new CurrencyMetadata(new CurrencyCode("USD"), 2));
    when(clock.instant()).thenReturn(Instant.parse("2026-01-05T12:00:00Z"));
    when(pricingResolver.resolve("DUPLICATA_MERCANTIL"))
        .thenReturn(
            new ResolvedPricingStrategy(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                new ReceivableTypeCode("DUPLICATA_MERCANTIL"),
                "DUPLICATA_MERCANTIL",
                0,
                new BigDecimal("0.015")));
    when(baseRateQuery.findApplicable(any(CurrencyCode.class), any(LocalDate.class)))
        .thenReturn(
            new BaseRate(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "BRL",
                new BigDecimal("0.010000000000"),
                LocalDate.of(2026, 1, 1),
                "DEMO_SEED"));
    when(decimalPower.pow(any(BigDecimal.class), any(BigDecimal.class)))
        .thenReturn(new BigDecimal("1.025"));
  }

  @Test
  void inactive_receivable_type_is_a_safe_422_without_partial_result() throws Exception {
    when(pricingResolver.resolve("DUPLICATA_MERCANTIL"))
        .thenThrow(new ReceivableTypeInactiveException());

    assertProblem(422, "RECEIVABLE_TYPE_INACTIVE");
  }

  @Test
  void missing_strategy_is_a_safe_500_without_partial_result() throws Exception {
    when(pricingResolver.resolve("DUPLICATA_MERCANTIL"))
        .thenThrow(new PricingStrategyNotConfiguredException());

    assertProblem(500, "PRICING_STRATEGY_NOT_CONFIGURED");
  }

  @Test
  void currency_metadata_failure_is_a_safe_500_without_partial_result() throws Exception {
    when(currencyMetadataQuery.find("BRL"))
        .thenThrow(new CurrencyMetadataQueryException(new IllegalStateException("secret-db")));

    assertProblem(500, "PRICING_CALCULATION_FAILED");
  }

  @Test
  void base_rate_failure_is_a_safe_500_without_partial_result() throws Exception {
    when(baseRateQuery.findApplicable(any(CurrencyCode.class), any(LocalDate.class)))
        .thenThrow(new BaseRateQueryException(new IllegalStateException("secret-sql")));

    assertProblem(500, "PRICING_CALCULATION_FAILED");
  }

  @Test
  void decimal_power_failure_is_a_safe_500_without_partial_result() throws Exception {
    when(decimalPower.pow(any(BigDecimal.class), any(BigDecimal.class)))
        .thenThrow(new PricingCalculationException(new ArithmeticException("secret-math")));

    assertProblem(500, "PRICING_CALCULATION_FAILED");
  }

  @Test
  void unexpected_illegal_argument_is_not_misclassified_as_validation_error() throws Exception {
    when(decimalPower.pow(any(BigDecimal.class), any(BigDecimal.class)))
        .thenThrow(new IllegalArgumentException("secret-defect"));

    assertProblem(500, "PRICING_CALCULATION_FAILED");
  }

  @Test
  void missing_exchange_rate_is_a_safe_404_without_partial_result() throws Exception {
    when(exchangeRateQuery.find("BRL", "USD", Instant.parse("2026-01-05T12:00:00Z")))
        .thenThrow(new ExchangeRateNotFoundException());

    assertFxProblem(404, "EXCHANGE_RATE_NOT_FOUND");
  }

  @Test
  void expired_exchange_rate_is_a_safe_422_without_partial_result() throws Exception {
    when(exchangeRateQuery.find("BRL", "USD", Instant.parse("2026-01-05T12:00:00Z")))
        .thenThrow(new ExchangeRateExpiredException());

    assertFxProblem(422, "EXCHANGE_RATE_EXPIRED");
  }

  @Test
  void exchange_rate_infrastructure_failure_is_a_safe_500_without_partial_result()
      throws Exception {
    when(exchangeRateQuery.find("BRL", "USD", Instant.parse("2026-01-05T12:00:00Z")))
        .thenThrow(new ExchangeRateQueryException(new IllegalStateException("secret-sql")));

    assertFxProblem(500, "PRICING_CALCULATION_FAILED");
  }

  @Test
  void same_currency_does_not_query_or_emit_fx_metrics() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pricing/simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.presentValueInPaymentCurrency").value("975.61"))
        .andExpect(jsonPath("$.exchangeRate").value(org.hamcrest.Matchers.nullValue()));

    verifyNoInteractions(exchangeRateQuery);
    org.assertj.core.api.Assertions.assertThat(
            meterRegistry.find("srm.fx.conversion.failures").meters())
        .isEmpty();
  }

  private void assertProblem(int expectedStatus, String expectedCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pricing/simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.presentValue").doesNotExist())
        .andExpect(jsonPath("$.presentValueInPaymentCurrency").doesNotExist())
        .andExpect(jsonPath("$.exchangeRate").doesNotExist())
        .andExpect(jsonPath("$.discount").doesNotExist())
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  private void assertFxProblem(int expectedStatus, String expectedCode) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pricing/simulations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(FX_REQUEST))
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.presentValue").doesNotExist())
        .andExpect(jsonPath("$.presentValueInPaymentCurrency").doesNotExist())
        .andExpect(jsonPath("$.exchangeRate").doesNotExist())
        .andExpect(jsonPath("$.discount").doesNotExist())
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
