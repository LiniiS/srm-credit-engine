package com.srm.creditengine.currency.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srm.creditengine.currency.service.ExchangeRateService;
import com.srm.creditengine.currency.service.FxProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExchangeRateSyncApiTest {
  @Test
  void returns_safe_problem_detail_when_provider_is_unavailable() throws Exception {
    var service = org.mockito.Mockito.mock(ExchangeRateService.class);
    when(service.synchronize("USD", "BRL"))
        .thenThrow(new FxProviderUnavailableException(new RuntimeException("internal-url/sql")));
    var mvc =
        MockMvcBuilders.standaloneSetup(new ExchangeRateController(service))
            .setControllerAdvice(new ExchangeRateExceptionHandler())
            .build();

    mvc.perform(
            post("/api/v1/exchange-rates/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"BRL\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("FX_PROVIDER_UNAVAILABLE"))
        .andExpect(jsonPath("$.detail").value("FX provider unavailable"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal-url"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("RuntimeException"))));
  }
}
