package com.srm.creditengine.pricing.api;

import com.srm.creditengine.pricing.service.PricingSimulationCommand;
import com.srm.creditengine.pricing.service.PricingSimulationResult;
import com.srm.creditengine.pricing.service.PricingSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing/simulations")
final class PricingSimulationController {
  private final PricingSimulationService service;

  PricingSimulationController(PricingSimulationService service) {
    this.service = service;
  }

  @Operation(summary = "Simulate present value in the receivable currency")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Simulation calculated",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PricingSimulationResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = PricingSimulationProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Catalog reference not found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = PricingSimulationProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Business rule violation",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = PricingSimulationProblemDetail.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Safe calculation failure",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = PricingSimulationProblemDetail.class)))
  })
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<PricingSimulationResponse> simulate(
      @Valid @RequestBody PricingSimulationRequest request) {
    var result =
        service.simulate(
            new PricingSimulationCommand(
                new BigDecimal(request.faceValue()),
                request.currency(),
                request.receivableTypeCode(),
                request.calculationDate(),
                request.dueDate()));
    return ResponseEntity.ok(toResponse(result));
  }

  private PricingSimulationResponse toResponse(PricingSimulationResult result) {
    return new PricingSimulationResponse(
        result.faceValue().toPlainString(),
        result.currency(),
        result.receivableTypeCode(),
        result.calculationDate(),
        result.dueDate(),
        result.adjustedDueDate(),
        result.termDays(),
        result.termMonths().toPlainString(),
        result.baseRate().toPlainString(),
        result.baseRateId(),
        result.baseRateSource(),
        result.spread().toPlainString(),
        result.monthlyRate().toPlainString(),
        result.presentValue().toPlainString(),
        result.discount().toPlainString());
  }
}
