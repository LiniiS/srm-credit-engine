package com.srm.creditengine.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.architecturefixturesunknown.pricing.service.UnknownModuleConsumer;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  @Test
  void rules_reject_a_forbidden_dependency_in_test_fixtures() {
    var fixtures =
        new ClassFileImporter().importPackages("com.srm.creditengine.architecturefixtures");

    assertThatThrownBy(
            () -> ArchitectureRules.checkAll(fixtures, "com.srm.creditengine.architecturefixtures"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("depends on non-public or forbidden module type");
  }

  @Test
  void rules_reject_root_package_classes_other_than_the_application_bootstrap() {
    assertViolation("architecturefixturesroot", "RogueRootClass", "outside the approved");
  }

  @Test
  void rules_reject_bootstrap_access_to_module_internals() {
    assertViolation("architecturefixturesbootstrap", "bootstrap depends on non-public module type");
  }

  @Test
  void rules_reject_framework_dependencies_in_the_domain() {
    assertViolation("architecturefixturesframework", "domain", "springframework.web");
  }

  @Test
  void rules_reject_unknown_top_level_modules() {
    var rootPackage = "com.srm.creditengine.architecturefixturesunknown";
    var knownConsumerOnly = new ClassFileImporter().importClasses(UnknownModuleConsumer.class);

    assertThatThrownBy(() -> ArchitectureRules.checkAll(knownConsumerOnly, rootPackage))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "com.srm.creditengine.architecturefixturesunknown.pricing.service.UnknownModuleConsumer depends on a type outside the approved top-level module set: com.srm.creditengine.architecturefixturesunknown.legacy.api.LegacyApi");
  }

  @Test
  void rules_reject_cross_module_access_to_internals() {
    assertViolation(
        "architecturefixturesinternal", "depends on non-public or forbidden module type");
  }

  @Test
  void rules_reject_module_cycles() {
    assertViolation("architecturefixturescycle", "Cycle detected");
  }

  @Test
  void rules_reject_api_access_to_persistence() {
    assertViolation("architecturefixturesapi", "api", "persistence");
  }

  @Test
  void rules_reject_service_access_to_api_and_persistence() {
    assertViolation("architecturefixturesservice", "api", "persistence");
  }

  @Test
  void rules_reject_domain_access_to_api_service_and_persistence() {
    assertViolation("architecturefixturesdomain", "api", "service", "persistence");
  }

  @Test
  void rules_reject_persistence_access_to_api_and_service() {
    assertViolation("architecturefixturespersistence", "api", "service");
  }

  @Test
  void rules_allow_reporting_api_to_access_reporting_persistence() {
    var rootPackage = "com.srm.creditengine.architecturefixturesreporting";
    var fixtures = new ClassFileImporter().importPackages(rootPackage);

    ArchitectureRules.checkAll(fixtures, rootPackage);
  }

  private static void assertViolation(String fixtureSuffix, String... messageFragments) {
    var rootPackage = "com.srm.creditengine." + fixtureSuffix;
    var fixtures = new ClassFileImporter().importPackages(rootPackage);
    var assertion =
        assertThatThrownBy(() -> ArchitectureRules.checkAll(fixtures, rootPackage))
            .isInstanceOf(AssertionError.class);
    for (var fragment : messageFragments) {
      assertion.hasMessageContaining(fragment);
    }
  }
}
