package com.srm.creditengine.currency.api;

import com.srm.creditengine.currency.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {
  private final ExchangeRateService service;

  public ExchangeRateController(ExchangeRateService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(summary = "Register an append-only exchange-rate version")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Exchange-rate version created",
        headers = @Header(name = "Location", description = "Created resource URI"),
        content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error or unsupported currency",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ExchangeRateProblemDetail.class)))
  })
  ResponseEntity<ExchangeRateResponse> register(@Valid @RequestBody ExchangeRateRequest request) {
    var result =
        service.register(
            request.baseCurrency(),
            request.quoteCurrency(),
            request.rate(),
            request.source(),
            request.effectiveAt());
    return ResponseEntity.created(URI.create("/api/v1/exchange-rates/" + result.id()))
        .body(ExchangeRateResponse.from(result));
  }

  @PostMapping("/sync")
  @Operation(summary = "Synchronize an exchange-rate version from the configured FX provider")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Exchange-rate version synchronized",
        headers = @Header(name = "Location", description = "Created resource URI"),
        content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error or unsupported currency",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ExchangeRateProblemDetail.class))),
    @ApiResponse(
        responseCode = "503",
        description = "FX provider unavailable",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ExchangeRateProblemDetail.class)))
  })
  ResponseEntity<ExchangeRateResponse> synchronize(
      @Valid @RequestBody ExchangeRateSyncRequest request) {
    var result = service.synchronize(request.baseCurrency(), request.quoteCurrency());
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/exchange-rates/" + result.id()))
        .body(ExchangeRateResponse.from(result));
  }

  @GetMapping("/latest")
  @Operation(summary = "Get the exchange rate effective at the server clock instant")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Latest effective exchange rate",
        content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error or unsupported currency",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ExchangeRateProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "No effective exchange rate found",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ExchangeRateProblemDetail.class)))
  })
  ExchangeRateResponse latest(
      @RequestParam("base") @Pattern(regexp = "[A-Z]{3}") String base,
      @RequestParam("quote") @Pattern(regexp = "[A-Z]{3}") String quote) {
    return ExchangeRateResponse.from(service.latest(base, quote));
  }
}
