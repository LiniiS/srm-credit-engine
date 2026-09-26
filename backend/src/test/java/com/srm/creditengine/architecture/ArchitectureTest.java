package com.srm.creditengine.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.currency.domain.port.BaseRateQuery;
import com.srm.creditengine.currency.domain.port.CurrencyMetadataQuery;
import com.srm.creditengine.currency.domain.port.ExchangeRateProvider;
import com.srm.creditengine.currency.domain.port.ExchangeRateRepository;
import com.srm.creditengine.pricing.domain.port.ReceivableTypeCatalog;
import com.srm.creditengine.pricing.domain.port.ReceivableTypePricingResolver;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

  @Test
  void production_code_obeys_architecture_rules() {
    var productionClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.srm.creditengine");

    ArchitectureRules.checkAll(productionClasses, "com.srm.creditengine");
  }

  @Test
  void currency_exposes_only_its_approved_public_ports() {
    var productionClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.srm.creditengine.currency.domain.port");
    var publicPortInterfaces =
        productionClasses.stream()
            .filter(type -> type.isInterface())
            .filter(type -> type.getPackageName().contains(".domain.port"))
            .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
            .map(type -> type.getName())
            .toList();

    assertThat(publicPortInterfaces)
        .containsExactlyInAnyOrder(
            BaseRateQuery.class.getName(),
            CurrencyMetadataQuery.class.getName(),
            ExchangeRateProvider.class.getName(),
            ExchangeRateRepository.class.getName());
  }

  @Test
  void pricing_exposes_only_its_approved_internal_ports() {
    var productionClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.srm.creditengine.pricing");
    var publicPortInterfaces =
        productionClasses.stream()
            .filter(type -> type.isInterface())
            .filter(type -> type.getPackageName().contains(".domain.port"))
            .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
            .map(type -> type.getName())
            .toList();

    assertThat(publicPortInterfaces)
        .containsExactlyInAnyOrder(
            ReceivableTypeCatalog.class.getName(), ReceivableTypePricingResolver.class.getName());
    var httpAdapters =
        productionClasses.stream()
            .filter(
                type ->
                    type.isAnnotatedWith(RestController.class)
                        || type.isAnnotatedWith(Controller.class))
            .toList();
    assertThat(httpAdapters)
        .isNotEmpty()
        .allMatch(type -> type.getPackageName().equals("com.srm.creditengine.pricing.api"));
  }
}
