package com.srm.creditengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.pricing.domain.ReceivableTypeInactiveException;
import com.srm.creditengine.pricing.domain.ReceivableTypeNotFoundException;
import com.srm.creditengine.pricing.domain.port.ReceivableTypePricingResolver;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class ReceivableTypeIntegrationTest {
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
  @Autowired ReceivableTypePricingResolver resolver;

  @AfterEach
  void removeFixtures() {
    jdbcTemplate.update("DELETE FROM receivable_type WHERE code LIKE 'TEST_%'");
    jdbcTemplate.update("UPDATE receivable_type SET active=TRUE WHERE code='DUPLICATA_MERCANTIL'");
  }

  @Test
  void migration_creates_exact_columns_and_reproducible_seeds_without_spread_or_class_name() {
    var columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type,
                   COALESCE(character_maximum_length, -1) AS max_length, is_nullable
            FROM information_schema.columns
            WHERE table_schema='public' AND table_name='receivable_type'
            ORDER BY ordinal_position
            """);
    var rows =
        jdbcTemplate.queryForList(
            """
            SELECT id::text, code, name, strategy_key, active, version
            FROM receivable_type ORDER BY code
            """);
    var constraints =
        jdbcTemplate.queryForList(
            """
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'public.receivable_type'::regclass
            ORDER BY conname
            """,
            String.class);

    assertThat(columns)
        .containsExactly(
            Map.of(
                "column_name", "id",
                "data_type", "uuid",
                "max_length", -1,
                "is_nullable", "NO"),
            Map.of(
                "column_name", "code",
                "data_type", "character varying",
                "max_length", 64,
                "is_nullable", "NO"),
            Map.of(
                "column_name", "name",
                "data_type", "character varying",
                "max_length", 100,
                "is_nullable", "NO"),
            Map.of(
                "column_name", "strategy_key",
                "data_type", "character varying",
                "max_length", 64,
                "is_nullable", "NO"),
            Map.of(
                "column_name", "active",
                "data_type", "boolean",
                "max_length", -1,
                "is_nullable", "NO"),
            Map.of(
                "column_name", "version",
                "data_type", "bigint",
                "max_length", -1,
                "is_nullable", "NO"));
    assertThat(constraints)
        .containsExactly(
            "ck_receivable_type_code_not_blank",
            "ck_receivable_type_name_not_blank",
            "ck_receivable_type_strategy_key_not_blank",
            "ck_receivable_type_version_non_negative",
            "receivable_type_pkey",
            "uk_receivable_type_code");
    assertThat(rows)
        .containsExactly(
            Map.of(
                "id", "44444444-4444-4444-8444-444444444444",
                "code", "CHEQUE_PRE_DATADO",
                "name", "Cheque Pré-datado",
                "strategy_key", "CHEQUE_PRE_DATADO",
                "active", true,
                "version", 0L),
            Map.of(
                "id", "33333333-3333-4333-8333-333333333333",
                "code", "DUPLICATA_MERCANTIL",
                "name", "Duplicata Mercantil",
                "strategy_key", "DUPLICATA_MERCANTIL",
                "active", true,
                "version", 0L));
  }

  @Test
  void resolver_returns_both_seed_strategies_and_distinguishes_missing_from_inactive() {
    assertThat(resolver.resolve("DUPLICATA_MERCANTIL").monthlySpread())
        .isEqualByComparingTo("0.015");
    assertThat(resolver.resolve("CHEQUE_PRE_DATADO").monthlySpread()).isEqualByComparingTo("0.025");
    assertThatThrownBy(() -> resolver.resolve("CONTRATO"))
        .isInstanceOf(ReceivableTypeNotFoundException.class);

    jdbcTemplate.update("UPDATE receivable_type SET active=FALSE WHERE code='DUPLICATA_MERCANTIL'");
    assertThatThrownBy(() -> resolver.resolve("DUPLICATA_MERCANTIL"))
        .isInstanceOf(ReceivableTypeInactiveException.class);
  }

  @Test
  void database_constraints_reject_blank_fields_duplicate_code_and_negative_version() {
    insertInvalid("TEST_BLANK", " ", "KEY", 0);
    insertInvalid("TEST_NAME", "Name", " ", 0);
    insertInvalid("TEST_VERSION", "Name", "KEY", -1);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO receivable_type VALUES (?, NULL, ?, ?, TRUE, 0)",
                    UUID.randomUUID(),
                    "Name",
                    "KEY"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO receivable_type VALUES (?, ?, ?, ?, TRUE, 0)",
                    UUID.randomUUID(),
                    "DUPLICATA_MERCANTIL",
                    "Duplicate",
                    "KEY"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertInvalid(String code, String name, String strategyKey, long version) {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO receivable_type VALUES (?, ?, ?, ?, TRUE, ?)",
                    UUID.randomUUID(),
                    code,
                    name,
                    strategyKey,
                    version))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
