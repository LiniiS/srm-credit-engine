package com.srm.creditengine.pricing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record PricingSimulationRequest(
    @NotBlank
        @Pattern(
            regexp = "^(?=.*[1-9])(?:0|[1-9][0-9]{0,16})(?:\\.[0-9]{1,2})?$",
            message = "must be a positive decimal string with at most 2 fraction digits")
        String faceValue,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String receivableTypeCode,
    @NotNull LocalDate calculationDate,
    @NotNull LocalDate dueDate) {}
