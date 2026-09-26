package com.srm.creditengine.pricing.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.srm.creditengine.pricing.domain.ReceivableTypeCode;
import com.srm.creditengine.pricing.domain.ReceivableTypeQueryException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class JpaReceivableTypeCatalogTest {

  @Test
  void translates_transaction_open_failure() {
    var fixture = fixture();
    when(fixture.transactionManager().getTransaction(any(TransactionDefinition.class)))
        .thenThrow(new CannotCreateTransactionException("database detail"));

    assertTranslatedFailure(fixture, CannotCreateTransactionException.class);
    verifyNoInteractions(fixture.repository());
  }

  @Test
  void translates_query_failure_and_rolls_back() {
    var fixture = fixtureWithStartedTransaction();
    when(fixture.repository().findByCode("DUPLICATA_MERCANTIL"))
        .thenThrow(new DataAccessResourceFailureException("database detail"));

    assertTranslatedFailure(fixture, DataAccessResourceFailureException.class);
    verify(fixture.transactionManager()).rollback(any(TransactionStatus.class));
  }

  @Test
  void translates_transaction_completion_failure() {
    var fixture = fixtureWithStartedTransaction();
    when(fixture.repository().findByCode("DUPLICATA_MERCANTIL")).thenReturn(Optional.empty());
    doThrow(new TransactionSystemException("commit detail"))
        .when(fixture.transactionManager())
        .commit(any(TransactionStatus.class));

    assertTranslatedFailure(fixture, TransactionSystemException.class);
  }

  private static void assertTranslatedFailure(
      Fixture fixture, Class<? extends RuntimeException> infrastructureType) {
    assertThatThrownBy(
            () -> fixture.catalog().findByCode(new ReceivableTypeCode("DUPLICATA_MERCANTIL")))
        .isInstanceOf(ReceivableTypeQueryException.class)
        .hasMessage("RECEIVABLE_TYPE_QUERY_FAILED")
        .hasCauseInstanceOf(infrastructureType);
  }

  private static Fixture fixtureWithStartedTransaction() {
    var fixture = fixture();
    when(fixture.transactionManager().getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    return fixture;
  }

  private static Fixture fixture() {
    var repository = mock(JpaReceivableTypeRepository.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    return new Fixture(
        new JpaReceivableTypeCatalog(repository, transactionManager),
        repository,
        transactionManager);
  }

  private record Fixture(
      JpaReceivableTypeCatalog catalog,
      JpaReceivableTypeRepository repository,
      PlatformTransactionManager transactionManager) {}
}
