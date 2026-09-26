package com.srm.creditengine.pricing.api;

import com.srm.creditengine.pricing.service.PricingSimulationException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
final class PricingSimulationExceptionHandler {
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
    var detail = ProblemDetail.forStatusAndDetail(status, exception.title());
    detail.setTitle(exception.title());
    detail.setType(
        URI.create(
            "https://srm.example/problems/" + exception.code().toLowerCase().replace('_', '-')));
    detail.setInstance(URI.create(request.getRequestURI()));
    detail.setProperty("code", exception.code());
    return ResponseEntity.status(status).body(detail);
  }
}
