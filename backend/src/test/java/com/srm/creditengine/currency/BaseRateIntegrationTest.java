package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.BaseRateCurrencyNotSupportedException;
import com.srm.creditengine.currency.domain.port.BaseRateNotFoundException;
import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest
class BaseRateIntegrationTest {
  private static final LocalDate SEED_DATE = LocalDate.parse("2026-01-01");

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

  @Autowired BaseRateQuery baseRateQuery;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void removeTestFixtures() {
    jdbcTemplate.update("DELETE FROM base_rate WHERE source = 'TEST_FIXTURE'");
  }

  @Test
  void migration_creates_exact_reproducible_demo_seeds() {
    var rows =
        jdbcTemplate.queryForList(
            """
            SELECT id::text, currency_code, rate_monthly::text, effective_from::text, source
            FROM base_rate
            WHERE source = 'DEMO_SEED'
            ORDER BY currency_code
            """);

    assertThat(rows)
        .containsExactly(
            Map.of(
                "id", "11111111-1111-4111-8111-111111111111",
                "currency_code", "BRL",
                "rate_monthly", "0.010000000000",
                "effective_from", "2026-01-01",
                "source", "DEMO_SEED"),
            Map.of(
                "id", "22222222-2222-4222-8222-222222222222",
                "currency_code", "USD",
                "rate_monthly", "0.005000000000",
                "effective_from", "2026-01-01",
                "source", "DEMO_SEED"));
  }

  @Test
  void query_returns_seed_with_exact_value_identity_effectivity_and_source() {
    var result = findApplicable("BRL", SEED_DATE);

    assertThat(result.id().toString()).isEqualTo("11111111-1111-4111-8111-111111111111");
    assertThat(result.currencyCode()).isEqualTo("BRL");
    assertThat(result.rateMonthly()).isEqualByComparingTo("0.010000000000");
    assertThat(result.effectiveFrom()).isEqualTo(SEED_DATE);
    assertThat(result.source()).isEqualTo("DEMO_SEED");
  }

  @Test
  void query_selects_latest_inclusive_version_and_never_returns_future_early() {
    insertFixture("33333333-3333-4333-8333-333333333333", "BRL", "0.020000000000", "2026-02-01");
    insertFixture("44444444-4444-4444-8444-444444444444", "BRL", "0.030000000000", "2026-03-01");

    assertThat(findApplicable("BRL", LocalDate.parse("2026-01-31")).rateMonthly())
        .isEqualByComparingTo("0.010000000000");
    assertThat(findApplicable("BRL", LocalDate.parse("2026-02-01")).rateMonthly())
        .isEqualByComparingTo("0.020000000000");
    assertThat(findApplicable("BRL", LocalDate.parse("2026-02-28")).rateMonthly())
        .isEqualByComparingTo("0.020000000000");
  }

  @Test
  void query_distinguishes_unsupported_currency_from_missing_applicable_version() {
    assertThatThrownBy(() -> findApplicable("EUR", SEED_DATE))
        .isInstanceOf(BaseRateCurrencyNotSupportedException.class)
        .hasMessageContaining("CURRENCY_NOT_SUPPORTED");
    assertThatThrownBy(() -> findApplicable("BRL", SEED_DATE.minusDays(1)))
        .isInstanceOf(BaseRateNotFoundException.class)
        .hasMessage("BASE_RATE_NOT_FOUND");
  }

  @Test
  void query_rejects_malformed_currency_and_null_date_before_database_lookup() {
    assertThatThrownBy(() -> new CurrencyCode("brl")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> baseRateQuery.findApplicable(new CurrencyCode("BRL"), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void history_is_append_only_through_the_public_surface() {
    insertFixture("55555555-5555-4555-8555-555555555555", "USD", "0.006000000000", "2026-02-01");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM base_rate WHERE currency_code='USD'", Integer.class))
        .isEqualTo(2);
    assertThat(findApplicable("USD", SEED_DATE).rateMonthly())
        .isEqualByComparingTo("0.005000000000");
    assertThat(findApplicable("USD", LocalDate.parse("2026-02-01")).rateMonthly())
        .isEqualByComparingTo("0.006000000000");
    assertThat(BaseRateQuery.class.getDeclaredMethods()).hasSize(1);
  }

  @Test
  void database_rejects_duplicate_negative_blank_source_and_unknown_currency() {
    insertFixture("66666666-6666-4666-8666-666666666666", "BRL", "0.011000000000", "2026-04-01");

    assertInvalidInsert(
        "77777777-7777-4777-8777-777777777777", "BRL", "0.012", "2026-04-01", "TEST_FIXTURE");
    assertInvalidInsert(
        "77777777-7777-4777-8777-777777777777", "BRL", "-0.001", "2026-05-01", "TEST_FIXTURE");
    assertInvalidInsert(
        "77777777-7777-4777-8777-777777777777", "BRL", "0.001", "2026-05-01", "   ");
    assertInvalidInsert(
        "77777777-7777-4777-8777-777777777777", "EUR", "0.001", "2026-05-01", "TEST_FIXTURE");
    assertInvalidInsert(
        "77777777-7777-4777-8777-777777777777", "BRL", "0.001", "2026-05-01", "A".repeat(65));
  }

  @Test
  void migration_defines_required_columns_constraints_and_index_order() {
    var columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type, numeric_precision, numeric_scale,
                   character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_schema='public' AND table_name='base_rate'
            ORDER BY ordinal_position
            """);
    var constraints =
        jdbcTemplate.queryForList(
            """
            SELECT contype, pg_get_constraintdef(oid) definition
            FROM pg_constraint
            WHERE conrelid='base_rate'::regclass
            """);
    var indexDefinition =
        jdbcTemplate.queryForObject(
            "SELECT indexdef FROM pg_indexes WHERE indexname='idx_base_rate_applicable'",
            String.class);

    assertThat(columns).hasSize(5);
    assertThat(columns)
        .anySatisfy(
            column -> {
              assertThat(column.get("column_name")).isEqualTo("rate_monthly");
              assertThat(column.get("data_type")).isEqualTo("numeric");
              assertThat(column.get("numeric_precision")).isEqualTo(18);
              assertThat(column.get("numeric_scale")).isEqualTo(12);
              assertThat(column.get("is_nullable")).isEqualTo("NO");
            })
        .anySatisfy(
            column -> {
              assertThat(column.get("column_name")).isEqualTo("source");
              assertThat(column.get("character_maximum_length")).isEqualTo(64);
              assertThat(column.get("is_nullable")).isEqualTo("NO");
            })
        .anySatisfy(
            column -> {
              assertThat(column.get("column_name")).isEqualTo("effective_from");
              assertThat(column.get("data_type")).isEqualTo("date");
            });
    assertThat(constraints)
        .extracting(row -> row.get("definition").toString())
        .anyMatch(
            definition ->
                definition.contains("FOREIGN KEY (currency_code) REFERENCES currency(code)"))
        .anyMatch(definition -> definition.contains("UNIQUE (currency_code, effective_from)"))
        .anyMatch(definition -> definition.contains("CHECK ((rate_monthly >= (0)::numeric))"))
        .anyMatch(definition -> definition.contains("CHECK ((btrim((source)::text) <> ''::text))"));
    assertThat(indexDefinition).contains("(currency_code, effective_from DESC)");
  }

  private void insertFixture(String id, String currency, String value, String effectiveFrom) {
    jdbcTemplate.update(
        """
        INSERT INTO base_rate (id, currency_code, rate_monthly, effective_from, source)
        VALUES (?::uuid, ?, ?::numeric, ?::date, 'TEST_FIXTURE')
        """,
        id,
        currency,
        value,
        effectiveFrom);
  }

  private com.srm.creditengine.currency.domain.port.BaseRate findApplicable(
      String currency, LocalDate calculationDate) {
    return baseRateQuery.findApplicable(new CurrencyCode(currency), calculationDate);
  }

  private void assertInvalidInsert(
      String id, String currency, String value, String effectiveFrom, String source) {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO base_rate (id, currency_code, rate_monthly, effective_from, source)
                    VALUES (?::uuid, ?, ?::numeric, ?::date, ?)
                    """,
                    id,
                    currency,
                    value,
                    effectiveFrom,
                    source))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
