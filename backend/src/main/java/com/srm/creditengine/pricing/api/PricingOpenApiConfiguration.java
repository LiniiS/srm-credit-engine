package com.srm.creditengine.pricing.api;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PricingOpenApiConfiguration {

  @Bean
  OpenApiCustomizer nullablePricingExchangeRate() {
    return openApi -> {
      var response = openApi.getComponents().getSchemas().get("PricingSimulationResponse");
      if (response != null && response.getProperties() != null) {
        var exchangeRate = response.getProperties().get("exchangeRate");
        if (exchangeRate instanceof Schema<?> exchangeRateSchema) {
          var nullableSchema =
              new ComposedSchema()
                  .addOneOfItem(exchangeRateSchema)
                  .addOneOfItem(new Schema<>().types(Set.of("null")));
          response.addProperty("exchangeRate", nullableSchema);
        }
      }
    };
  }
}
