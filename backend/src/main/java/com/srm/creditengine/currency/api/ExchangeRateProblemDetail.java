package com.srm.creditengine.currency.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

@Schema(description = "RFC 9457 problem detail with stable domain code")
record ExchangeRateProblemDetail(
    @Schema(example = "https://srm.example/problems/validation-error") URI type,
    @Schema(example = "Validation failed") String title,
    @Schema(example = "400") int status,
    @Schema(example = "Validation failed") String detail,
    @Schema(example = "/api/v1/exchange-rates") URI instance,
    @Schema(example = "VALIDATION_ERROR", requiredMode = Schema.RequiredMode.REQUIRED) String code,
    List<ExchangeRateViolation> violations) {}
