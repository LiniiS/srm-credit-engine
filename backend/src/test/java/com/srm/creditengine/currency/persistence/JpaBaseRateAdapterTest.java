package com.srm.creditengine.currency.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.currency.domain.CurrencyCode;
import com.srm.creditengine.currency.domain.port.BaseRateQueryException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class JpaBaseRateAdapterTest {

  @Test
  void translates_persistence_failure_at_the_public_port() {
    var repository = mock(JpaBaseRateRepository.class);
    var entityManager = mock(EntityManager.class);
    when(entityManager.find(CurrencyEntity.class, "BRL")).thenReturn(new CurrencyEntity());
    when(repository.findFirstByCurrencyCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            "BRL", LocalDate.parse("2026-01-01")))
        .thenThrow(new DataAccessResourceFailureException("database detail"));
    var adapter = new JpaBaseRateAdapter(repository, entityManager);

    assertThatThrownBy(
            () -> adapter.findApplicable(new CurrencyCode("BRL"), LocalDate.parse("2026-01-01")))
        .isInstanceOf(BaseRateQueryException.class)
        .hasMessage(BaseRateQueryException.CODE)
        .hasCauseInstanceOf(DataAccessResourceFailureException.class);
  }
}
