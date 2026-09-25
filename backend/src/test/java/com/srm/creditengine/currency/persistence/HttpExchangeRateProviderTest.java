package com.srm.creditengine.currency.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.ExchangeRateProviderException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpExchangeRateProviderTest {
  private HttpServer server;
  private ExecutorService serverExecutor;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    if (serverExecutor != null) {
      serverExecutor.shutdownNow();
    }
  }

  @Test
  void retries_transient_failure_and_returns_valid_payload_once_recovered() throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          if (calls.incrementAndGet() < 3) {
            respond(exchange, 503, "text/plain", "unavailable");
          } else {
            respond(exchange, 200, "application/json", validPayload());
          }
        });

    var registry = new SimpleMeterRegistry();
    var result = provider(Duration.ofSeconds(1), registry).fetch(currency("USD"), currency("BRL"));

    assertThat(calls).hasValue(3);
    assertThat(result.rate()).isEqualByComparingTo("5.12345678");
    assertThat(result.source()).isEqualTo("LOCAL_FX_MOCK");
    assertThat(registry.counter("srm.fx.provider.retries", "outcome", "retry").count())
        .isEqualTo(2);
    assertThat(registry.counter("srm.fx.provider.calls", "outcome", "success").count())
        .isEqualTo(1);
    assertThat(registry.timer("srm.fx.provider.duration", "outcome", "success").count()).isOne();
    assertThat(registry.get("srm.fx.provider.circuit.state").gauge().value())
        .isEqualTo(CircuitBreaker.State.CLOSED.ordinal());
  }

  @Test
  void does_not_retry_client_error_or_invalid_payload() throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 422, "application/json", "{}");
        });
    var clientErrorProvider = provider(Duration.ofSeconds(1), new SimpleMeterRegistry());

    assertThatThrownBy(() -> clientErrorProvider.fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(1);
    assertThat(clientErrorProvider.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();

    server.stop(0);
    calls.set(0);
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 200, "application/json", "{\"rate\":\"invalid\"}");
        });

    assertThatThrownBy(
            () ->
                provider(Duration.ofSeconds(1), new SimpleMeterRegistry())
                    .fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(1);
  }

  @ParameterizedTest
  @MethodSource("invalidPayloads")
  void rejects_each_invalid_payload_without_retry_and_counts_one_logical_failure(
      String contentType, String payload) throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 200, contentType, payload);
        });
    var registry = new SimpleMeterRegistry();
    var provider = provider(Duration.ofSeconds(1), registry);

    assertThatThrownBy(() -> provider.fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(1);
    assertThat(registry.counter("srm.fx.provider.retries", "outcome", "retry").count()).isZero();
    assertThat(provider.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isOne();
  }

  @Test
  void rejects_oversized_provider_body_without_retry() throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 200, "application/json", "x".repeat(16 * 1024 + 1));
        });
    var registry = new SimpleMeterRegistry();

    assertThatThrownBy(
            () -> provider(Duration.ofSeconds(1), registry).fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(1);
    assertThat(registry.counter("srm.fx.provider.retries", "outcome", "retry").count()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 504})
  void retries_only_allowlisted_server_errors(int status) throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, status, "text/plain", "unavailable");
        });

    assertThatThrownBy(
            () ->
                provider(Duration.ofSeconds(1), new SimpleMeterRegistry())
                    .fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(3);
  }

  @Test
  void does_not_retry_non_allowlisted_server_error() throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 501, "text/plain", "not implemented");
        });

    assertThatThrownBy(
            () ->
                provider(Duration.ofSeconds(1), new SimpleMeterRegistry())
                    .fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void retries_io_failures_and_exposes_the_approved_backoff_configuration() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    var unavailablePort = server.getAddress().getPort();
    server.stop(0);
    var registry = new SimpleMeterRegistry();
    var properties =
        properties(
            "http://localhost:" + unavailablePort, Duration.ofMillis(100), Duration.ofMillis(100));
    var provider = new HttpExchangeRateProvider(properties, new ObjectMapper(), registry);

    assertThatThrownBy(() -> provider.fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(registry.counter("srm.fx.provider.retries", "outcome", "retry").count())
        .isEqualTo(2);
    assertThat(provider.retry().getRetryConfig().getMaxAttempts()).isEqualTo(3);
    assertThat(provider.retry().getRetryConfig().getIntervalFunction().apply(1)).isEqualTo(100L);
    assertThat(provider.retry().getRetryConfig().getIntervalFunction().apply(2)).isEqualTo(200L);
  }

  @Test
  void configures_approved_circuit_breaker_and_allows_two_half_open_calls() throws Exception {
    start(exchange -> respond(exchange, 200, "application/json", validPayload()));
    var provider = provider(Duration.ofSeconds(1), new SimpleMeterRegistry());
    var breaker = provider.circuitBreaker();

    assertThat(breaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(4);
    assertThat(breaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(4);
    assertThat(breaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
    assertThat(breaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
        .isEqualTo(5000L);
    assertThat(breaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
        .isEqualTo(2);
    breaker.transitionToOpenState();
    breaker.transitionToHalfOpenState();
    assertThat(breaker.tryAcquirePermission()).isTrue();
    assertThat(breaker.tryAcquirePermission()).isTrue();
    assertThat(breaker.tryAcquirePermission()).isFalse();
  }

  @Test
  void opens_count_based_circuit_after_four_logical_failures_and_rejects_without_http_call()
      throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          respond(exchange, 503, "text/plain", "unavailable");
        });
    var registry = new SimpleMeterRegistry();
    var provider = provider(Duration.ofSeconds(1), registry);

    for (int operation = 0; operation < 4; operation++) {
      assertThatThrownBy(() -> provider.fetch(currency("USD"), currency("BRL")))
          .isInstanceOf(ExchangeRateProviderException.class);
    }
    assertThat(provider.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(calls).hasValue(12);
    var callsBeforeRejection = calls.get();

    assertThatThrownBy(() -> provider.fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(calls).hasValue(callsBeforeRejection);
    assertThat(registry.get("srm.fx.provider.circuit.state").gauge().value())
        .isEqualTo(CircuitBreaker.State.OPEN.ordinal());
    assertThat(registry.counter("srm.fx.provider.circuit.transitions", "state", "open").count())
        .isOne();
  }

  @Test
  void applies_timeout_to_each_attempt() throws Exception {
    var calls = new AtomicInteger();
    start(
        exchange -> {
          calls.incrementAndGet();
          try {
            new CountDownLatch(1).await(2, TimeUnit.SECONDS);
            respond(exchange, 200, "application/json", validPayload());
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          } catch (IOException ignored) {
            // The timed-out client closes the exchange before the delayed response is written.
          }
        });

    var registry = new SimpleMeterRegistry();
    assertThatThrownBy(
            () ->
                provider(Duration.ofMillis(500), registry).fetch(currency("USD"), currency("BRL")))
        .isInstanceOf(ExchangeRateProviderException.class);
    assertThat(registry.counter("srm.fx.provider.retries", "outcome", "retry").count())
        .isEqualTo(2);
    assertThat(calls.get()).isBetween(1, 3);
  }

  private HttpExchangeRateProvider provider(Duration timeout, SimpleMeterRegistry registry) {
    var properties =
        properties(
            "http://localhost:" + server.getAddress().getPort(), timeout, Duration.ofMillis(1));
    return new HttpExchangeRateProvider(properties, new ObjectMapper(), registry);
  }

  private FxProviderProperties properties(String baseUrl, Duration timeout, Duration backoff) {
    return new FxProviderProperties(
        baseUrl, timeout, 3, backoff, 4, 4, 50, Duration.ofSeconds(5), 2);
  }

  private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    serverExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    server.setExecutor(serverExecutor);
    server.createContext("/v1/rates", handler);
    server.start();
  }

  private void respond(
      com.sun.net.httpserver.HttpExchange exchange, int status, String contentType, String body)
      throws IOException {
    assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("base=USD&quote=BRL");
    assertThat(exchange.getRequestHeaders().keySet())
        .noneMatch(header -> header.toLowerCase().contains("scenario"));
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private String validPayload() {
    return """
        {"baseCurrency":"USD","quoteCurrency":"BRL","rate":"5.12345678",
         "effectiveAt":"2026-09-24T12:00:00Z","source":"LOCAL_FX_MOCK"}
        """;
  }

  private static Stream<Arguments> invalidPayloads() {
    return Stream.of(
        Arguments.of("text/plain", validPayloadText()),
        Arguments.of("application/json", validPayloadText().replace("\"USD\"", "\"EUR\"")),
        Arguments.of("application/json", validPayloadText().replace("\"BRL\"", "\"EUR\"")),
        Arguments.of("application/json", validPayloadText().replace("5.12345678", "0.00000000")),
        Arguments.of("application/json", validPayloadText().replace("5.12345678", "5.123456789")),
        Arguments.of(
            "application/json", validPayloadText().replace("5.12345678", "12345678901.00000000")),
        Arguments.of(
            "application/json",
            validPayloadText().replace("2026-09-24T12:00:00Z", "not-an-instant")),
        Arguments.of("application/json", validPayloadText().replace("LOCAL_FX_MOCK", "   ")),
        Arguments.of(
            "application/json", validPayloadText().replace("LOCAL_FX_MOCK", "x".repeat(101))));
  }

  private static String validPayloadText() {
    return """
        {"baseCurrency":"USD","quoteCurrency":"BRL","rate":"5.12345678",
         "effectiveAt":"2026-09-24T12:00:00Z","source":"LOCAL_FX_MOCK"}
        """;
  }

  private CurrencyCode currency(String value) {
    return new CurrencyCode(value);
  }
}
