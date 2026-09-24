package com.srm.creditengine.currency.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateRequest(
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String quoteCurrency,
    @NotNull @Positive @Digits(integer = 10, fraction = 8) BigDecimal rate,
    @NotBlank @Size(max = 100) String source,
    @NotNull Instant effectiveAt) {}
