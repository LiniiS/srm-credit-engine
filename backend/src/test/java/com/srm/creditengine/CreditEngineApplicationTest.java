package com.srm.creditengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
      "management.endpoint.health.group.readiness.include=readinessState"
    })
class CreditEngineApplicationTest {

  @MockBean ExchangeRateRepository exchangeRateRepository;

  @Autowired TestRestTemplate restTemplate;

  @Test
  void context_starts(ApplicationContext context) {
    assertThat(context).isNotNull();
  }

  @Test
  void readiness_reports_up_without_exposing_details() {
    var headers = new HttpHeaders();
    headers.setOrigin("http://localhost:5173");
    var response =
        restTemplate.exchange(
            "/actuator/health/readiness", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    assertThat(response.getHeaders().getAccessControlAllowOrigin())
        .isEqualTo("http://localhost:5173");
  }
}
