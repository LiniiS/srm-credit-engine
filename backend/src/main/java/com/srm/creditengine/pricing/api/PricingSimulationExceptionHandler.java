package com.srm.creditengine.pricing.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.srm.creditengine.pricing.service.PricingSimulationException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PricingSimulationController.class)
final class PricingSimulationExceptionHandler {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(PricingSimulationExceptionHandler.class);

  @ExceptionHandler(PricingSimulationException.class)
  ResponseEntity<ProblemDetail> simulation(
      PricingSimulationException exception, HttpServletRequest request) {
    var status =
        switch (exception.kind()) {
          case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
          case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return problem(status, exception.code(), exception.title(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> beanValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    var violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
            .toList();
    return validation(request, violations);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> unreadable(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    var mappingException = findMappingException(exception);
    if (mappingException == null || mappingException.getPath().isEmpty()) {
      return validation(request, List.of());
    }
    var field = mappingException.getPath().getLast().getFieldName();
    return field == null
        ? validation(request, List.of())
        : validation(request, List.of(Map.of("field", field, "message", "invalid value")));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
    LOGGER.error("Unexpected pricing simulation failure", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "PRICING_CALCULATION_FAILED",
        "Pricing calculation failed",
        request);
  }

  private ResponseEntity<ProblemDetail> validation(
      HttpServletRequest request, List<Map<String, String>> violations) {
    var response =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", request);
    response.getBody().setProperty("violations", violations);
    return response;
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String title, HttpServletRequest request) {
    var detail = ProblemDetail.forStatusAndDetail(status, title);
    detail.setTitle(title);
    detail.setType(
        URI.create("https://srm.example/problems/" + code.toLowerCase().replace('_', '-')));
    detail.setInstance(URI.create(request.getRequestURI()));
    detail.setProperty("code", code);
    return ResponseEntity.status(status).body(detail);
  }

  private JsonMappingException findMappingException(Throwable exception) {
    var current = exception;
    while (current != null) {
      if (current instanceof JsonMappingException mappingException) {
        return mappingException;
      }
      current = current.getCause();
    }
    return null;
  }
}
