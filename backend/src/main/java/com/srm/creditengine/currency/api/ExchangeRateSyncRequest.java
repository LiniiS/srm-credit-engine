package com.srm.creditengine.currency.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeRateSyncRequest(
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String quoteCurrency) {}
