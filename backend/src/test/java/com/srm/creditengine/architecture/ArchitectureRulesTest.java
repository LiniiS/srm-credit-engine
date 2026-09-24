package com.srm.creditengine.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void rules_reject_an_api_that_bypasses_the_service_layer() {
    assertViolation("architecturefixtureslayers", "api", "domain");
  }

  @Test
  void rules_reject_framework_dependencies_in_the_domain() {
    assertViolation("architecturefixturesframework", "domain", "springframework.web");
  }

  @Test
  void rules_reject_unknown_top_level_modules() {
    assertViolation("architecturefixturesunknown", "outside the approved top-level module set");
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
