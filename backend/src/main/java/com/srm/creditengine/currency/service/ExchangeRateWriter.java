package com.srm.creditengine.currency.service;

import com.srm.creditengine.currency.domain.ExchangeRate;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExchangeRateWriter {
  private final ExchangeRateRepository repository;

  ExchangeRateWriter(ExchangeRateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  ExchangeRate append(ExchangeRate exchangeRate) {
    return repository.append(exchangeRate);
  }
}
