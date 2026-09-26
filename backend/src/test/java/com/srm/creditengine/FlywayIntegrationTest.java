package com.srm.creditengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.6-alpine")
          .withDatabaseName("srm_credit_engine")
          .withUsername("srm")
          .withPassword("srm_test_password");

  @DynamicPropertySource
  static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

  @Test
  void flyway_initializes_an_empty_postgresql_database() {
    var installedRank =
        jdbcTemplate.queryForObject(
            "SELECT installed_rank FROM flyway_schema_history WHERE version = '1' AND success",
            Integer.class);
    var schemaVersion =
        jdbcTemplate.queryForObject(
            "SELECT metadata_value FROM application_metadata WHERE metadata_key = 'schema_version'",
            String.class);

    assertThat(installedRank).isEqualTo(1);
    assertThat(schemaVersion).isEqualTo("1");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT installed_rank FROM flyway_schema_history WHERE version = '2' AND success",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT installed_rank FROM flyway_schema_history WHERE version = '3' AND success",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT installed_rank FROM flyway_schema_history WHERE version = '4' AND success",
                Integer.class))
        .isEqualTo(4);
    assertThat(restTemplate.getForEntity("/actuator/health/readiness", String.class).getBody())
        .isEqualTo("{\"status\":\"UP\"}");
  }
}
