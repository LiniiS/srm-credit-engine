package com.srm.creditengine.pricing.domain.port;

import com.srm.creditengine.pricing.domain.ResolvedPricingStrategy;

/** Resolves the deployed pricing Strategy and auditable catalog identity for a type code. */
public interface ReceivableTypePricingResolver {
  ResolvedPricingStrategy resolve(String receivableTypeCode);
}
