package com.srm.creditengine.pricing.domain.port;

import com.srm.creditengine.pricing.domain.ReceivableType;
import com.srm.creditengine.pricing.domain.ReceivableTypeCode;
import java.util.Optional;

/** Reads receivable-type configuration without exposing persistence details. */
public interface ReceivableTypeCatalog {
  Optional<ReceivableType> findByCode(ReceivableTypeCode code);
}
