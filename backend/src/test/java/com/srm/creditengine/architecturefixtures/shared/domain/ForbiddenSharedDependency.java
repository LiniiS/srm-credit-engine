package com.srm.creditengine.architecturefixtures.shared.domain;

import com.srm.creditengine.architecturefixtures.pricing.api.PricingApi;

public final class ForbiddenSharedDependency {
  private final PricingApi pricingApi = new PricingApi();
}
