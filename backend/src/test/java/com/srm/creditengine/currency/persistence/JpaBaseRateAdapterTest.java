package com.srm.creditengine.currency.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.BaseRateQueryException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class JpaBaseRateAdapterTest {

  @Test
  void rejects_null_before_opening_a_transaction() {
    var repository = mock(JpaBaseRateRepository.class);
    var entityManager = mock(EntityManager.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    var adapter = new JpaBaseRateAdapter(repository, entityManager, transactionManager);

    assertThatThrownBy(() -> adapter.findApplicable(new CurrencyCode("BRL"), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("calculationDate");

    verifyNoInteractions(transactionManager, repository, entityManager);
  }

  @Test
  void translates_transaction_open_failure_at_the_public_port() {
    var fixture = fixture();
    when(fixture.transactionManager().getTransaction(any(TransactionDefinition.class)))
        .thenThrow(new CannotCreateTransactionException("database detail"));

    assertTranslatedFailure(fixture.adapter(), CannotCreateTransactionException.class);
    verifyNoInteractions(fixture.repository(), fixture.entityManager());
  }

  @Test
  void translates_execution_failure_at_the_public_port() {
    var fixture = fixtureWithStartedTransaction();
    when(fixture.entityManager().find(CurrencyEntity.class, "BRL"))
        .thenReturn(new CurrencyEntity());
    when(fixture
            .repository()
            .findFirstByCurrencyCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                "BRL", LocalDate.parse("2026-01-01")))
        .thenThrow(new DataAccessResourceFailureException("database detail"));

    assertTranslatedFailure(fixture.adapter(), DataAccessResourceFailureException.class);
    verify(fixture.transactionManager()).rollback(any(TransactionStatus.class));
  }

  @Test
  void translates_transaction_completion_failure_at_the_public_port() {
    var fixture = fixtureWithStartedTransaction();
    when(fixture.entityManager().find(CurrencyEntity.class, "BRL"))
        .thenReturn(new CurrencyEntity());
    var entity = new BaseRateEntity();
    entity.id = UUID.fromString("11111111-1111-4111-8111-111111111111");
    entity.currencyCode = "BRL";
    entity.rateMonthly = new BigDecimal("0.010000000000");
    entity.effectiveFrom = LocalDate.parse("2026-01-01");
    entity.source = "TEST_FIXTURE";
    when(fixture
            .repository()
            .findFirstByCurrencyCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                "BRL", LocalDate.parse("2026-01-01")))
        .thenReturn(Optional.of(entity));
    doThrow(new TransactionSystemException("commit detail"))
        .when(fixture.transactionManager())
        .commit(any(TransactionStatus.class));

    assertTranslatedFailure(fixture.adapter(), TransactionSystemException.class);
  }

  private void assertTranslatedFailure(
      JpaBaseRateAdapter adapter, Class<? extends RuntimeException> infrastructureType) {
    assertThatThrownBy(
            () -> adapter.findApplicable(new CurrencyCode("BRL"), LocalDate.parse("2026-01-01")))
        .isInstanceOf(BaseRateQueryException.class)
        .hasMessage(BaseRateQueryException.CODE)
        .hasCauseInstanceOf(infrastructureType);
  }

  private Fixture fixtureWithStartedTransaction() {
    var fixture = fixture();
    when(fixture.transactionManager().getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    return fixture;
  }

  private Fixture fixture() {
    var repository = mock(JpaBaseRateRepository.class);
    var entityManager = mock(EntityManager.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    return new Fixture(
        new JpaBaseRateAdapter(repository, entityManager, transactionManager),
        repository,
        entityManager,
        transactionManager);
  }

  private record Fixture(
      JpaBaseRateAdapter adapter,
      JpaBaseRateRepository repository,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager) {}
}
