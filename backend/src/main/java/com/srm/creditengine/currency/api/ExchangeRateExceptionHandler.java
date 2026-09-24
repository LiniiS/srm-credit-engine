package com.srm.creditengine.currency.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.srm.creditengine.currency.service.CurrencyNotSupportedException;
import com.srm.creditengine.currency.service.ExchangeRateNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ExchangeRateExceptionHandler {
  @ExceptionHandler(CurrencyNotSupportedException.class)
  ResponseEntity<ProblemDetail> unsupported(HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "CURRENCY_NOT_SUPPORTED", "Currency not supported", request);
  }

  @ExceptionHandler(ExchangeRateNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound(HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND, "EXCHANGE_RATE_NOT_FOUND", "Exchange rate not found", request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalid(HttpServletRequest request) {
    return validation(request, List.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> unreadable(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    var mappingException = findMappingException(exception);
    if (mappingException == null || mappingException.getPath().isEmpty()) {
      return validation(request, List.of());
    }
    var field = mappingException.getPath().getLast().getFieldName();
    if (field == null) {
      return validation(request, List.of());
    }
    return validation(request, List.of(Map.of("field", field, "message", "invalid value")));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> constraintValidation(
      ConstraintViolationException exception, HttpServletRequest request) {
    var violations =
        exception.getConstraintViolations().stream()
            .map(
                violation ->
                    Map.of(
                        "field",
                        lastPathSegment(violation.getPropertyPath().toString()),
                        "message",
                        violation.getMessage()))
            .toList();
    return validation(request, violations);
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

  private String lastPathSegment(String path) {
    var separator = path.lastIndexOf('.');
    return separator < 0 ? path : path.substring(separator + 1);
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
