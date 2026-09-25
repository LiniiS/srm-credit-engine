package com.srm.creditengine.currency.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.ExchangeRateProvider;
import com.srm.creditengine.currency.domain.port.ExchangeRateProviderException;
import com.srm.creditengine.currency.domain.port.ProvidedExchangeRate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
class HttpExchangeRateProvider implements ExchangeRateProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpExchangeRateProvider.class);
  private static final int MAX_PROVIDER_BODY_BYTES = 16 * 1024;

  private final RestClient client;
  private final ObjectMapper objectMapper;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;
  private final MeterRegistry meterRegistry;

  HttpExchangeRateProvider(
      FxProviderProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
    var httpClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.timeout());
    this.client =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.retry =
        Retry.of(
            "fxProvider",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(
                        properties.initialBackoff().toMillis(), 2.0))
                .retryOnException(HttpExchangeRateProvider::isRetryable)
                .build());
    this.circuitBreaker =
        CircuitBreaker.of(
            "fxProvider",
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.circuitBreakerWindowSize())
                .minimumNumberOfCalls(properties.circuitBreakerMinimumCalls())
                .failureRateThreshold(properties.circuitBreakerFailureRateThreshold())
                .waitDurationInOpenState(properties.circuitBreakerOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.circuitBreakerHalfOpenCalls())
                .ignoreException(exception -> exception instanceof PermanentClientException)
                .build());
    retry
        .getEventPublisher()
        .onRetry(
            event -> {
              meterRegistry.counter("srm.fx.provider.retries", "outcome", "retry").increment();
              LOGGER.warn("fx_provider_retry attempt={}", event.getNumberOfRetryAttempts());
            });
    circuitBreaker
        .getEventPublisher()
        .onStateTransition(
            event -> {
              meterRegistry
                  .counter(
                      "srm.fx.provider.circuit.transitions",
                      "state",
                      event.getStateTransition().getToState().name().toLowerCase())
                  .increment();
              LOGGER.info("fx_provider_circuit_breaker transition={}", event.getStateTransition());
            });
    meterRegistry.gauge(
        "srm.fx.provider.circuit.state", circuitBreaker, breaker -> breaker.getState().ordinal());
  }

  @Override
  public ProvidedExchangeRate fetch(CurrencyCode base, CurrencyCode quote) {
    var timer = Timer.start(meterRegistry);
    Supplier<RawResponse> retried =
        Retry.decorateSupplier(retry, () -> invoke(base.value(), quote.value()));
    Supplier<ProvidedExchangeRate> logicalCall =
        CircuitBreaker.decorateSupplier(circuitBreaker, () -> validate(retried.get(), base, quote));
    try {
      var result = logicalCall.get();
      meterRegistry.counter("srm.fx.provider.calls", "outcome", "success").increment();
      timer.stop(meterRegistry.timer("srm.fx.provider.duration", "outcome", "success"));
      LOGGER.info("fx_provider_call outcome=success");
      return result;
    } catch (CallNotPermittedException exception) {
      timer.stop(meterRegistry.timer("srm.fx.provider.duration", "outcome", "circuit_open"));
      recordFailure("circuit_open");
      throw new ExchangeRateProviderException("FX provider circuit is open", exception);
    } catch (PermanentClientException exception) {
      timer.stop(meterRegistry.timer("srm.fx.provider.duration", "outcome", "client_error"));
      recordFailure("client_error");
      throw new ExchangeRateProviderException("FX provider rejected the request", exception);
    } catch (RuntimeException exception) {
      timer.stop(meterRegistry.timer("srm.fx.provider.duration", "outcome", "failure"));
      recordFailure("failure");
      throw new ExchangeRateProviderException("FX provider request failed", exception);
    }
  }

  CircuitBreaker circuitBreaker() {
    return circuitBreaker;
  }

  Retry retry() {
    return retry;
  }

  private RawResponse invoke(String base, String quote) {
    try {
      var response =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/v1/rates")
                          .queryParam("base", base)
                          .queryParam("quote", quote)
                          .build())
              .exchange(
                  (request, value) -> {
                    var status = value.getStatusCode().value();
                    if (status >= 400 && status < 500) {
                      throw new PermanentClientException();
                    }
                    if (status == 500 || status == 502 || status == 503 || status == 504) {
                      throw new RetryableProviderException();
                    }
                    if (status != 200) {
                      throw new ProviderFailureException();
                    }
                    return new RawResponse(
                        value.getHeaders().getContentType(), readBoundedBody(value));
                  });
      return response;
    } catch (ResourceAccessException exception) {
      throw new RetryableProviderException(exception);
    }
  }

  private ProvidedExchangeRate validate(
      RawResponse response, CurrencyCode requestedBase, CurrencyCode requestedQuote) {
    if (response.contentType() == null
        || !MediaType.APPLICATION_JSON.isCompatibleWith(response.contentType())) {
      throw new InvalidProviderPayloadException();
    }
    try {
      var payload = objectMapper.readValue(response.body(), ProviderPayload.class);
      var base = new CurrencyCode(payload.baseCurrency());
      var quote = new CurrencyCode(payload.quoteCurrency());
      var rate = new BigDecimal(payload.rate());
      var effectiveAt = Instant.parse(payload.effectiveAt());
      var source = payload.source() == null ? "" : payload.source().trim();
      if (!base.equals(requestedBase)
          || !quote.equals(requestedQuote)
          || rate.signum() <= 0
          || rate.scale() > 8
          || rate.precision() - rate.scale() > 10
          || source.isEmpty()
          || source.length() > 100) {
        throw new InvalidProviderPayloadException();
      }
      return new ProvidedExchangeRate(base, quote, rate, effectiveAt, source);
    } catch (InvalidProviderPayloadException exception) {
      throw exception;
    } catch (RuntimeException | java.io.IOException exception) {
      throw new InvalidProviderPayloadException(exception);
    }
  }

  private void recordFailure(String outcome) {
    meterRegistry.counter("srm.fx.provider.calls", "outcome", outcome).increment();
    LOGGER.warn("fx_provider_call outcome={}", outcome);
  }

  private byte[] readBoundedBody(org.springframework.http.client.ClientHttpResponse response)
      throws java.io.IOException {
    var body = response.getBody().readNBytes(MAX_PROVIDER_BODY_BYTES + 1);
    if (body.length > MAX_PROVIDER_BODY_BYTES) {
      throw new InvalidProviderPayloadException();
    }
    return body;
  }

  private static boolean isRetryable(Throwable exception) {
    return exception instanceof RetryableProviderException;
  }

  private record RawResponse(MediaType contentType, byte[] body) {}

  private record ProviderPayload(
      String baseCurrency, String quoteCurrency, String rate, String effectiveAt, String source) {}

  private static final class RetryableProviderException extends RuntimeException {
    RetryableProviderException() {}

    RetryableProviderException(Throwable cause) {
      super(cause);
    }
  }

  private static final class PermanentClientException extends RuntimeException {}

  private static final class ProviderFailureException extends RuntimeException {}

  private static final class InvalidProviderPayloadException extends RuntimeException {
    InvalidProviderPayloadException() {}

    InvalidProviderPayloadException(Throwable cause) {
      super(cause);
    }
  }
}
