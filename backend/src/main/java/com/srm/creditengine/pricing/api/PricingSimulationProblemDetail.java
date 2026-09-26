package com.srm.creditengine.pricing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Schema(description = "RFC 9457 problem detail with stable domain code")
record PricingSimulationProblemDetail(
    URI type,
    String title,
    int status,
    String detail,
    URI instance,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    List<Map<String, String>> violations) {}
