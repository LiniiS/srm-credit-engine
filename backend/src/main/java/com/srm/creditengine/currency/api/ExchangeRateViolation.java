package com.srm.creditengine.currency.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Field validation violation")
record ExchangeRateViolation(
    @Schema(example = "quoteCurrency") String field,
    @Schema(example = "deve ser diferente de baseCurrency") String message) {}
