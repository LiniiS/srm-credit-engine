package com.srm.creditengine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ArchitectureRules {

  private static final Set<String> MODULES =
      Set.of("currency", "pricing", "settlement", "reporting", "shared");
  private static final Map<String, Set<String>> ALLOWED_MODULE_DEPENDENCIES =
      Map.of(
          "currency", Set.of("shared"),
          "pricing", Set.of("currency", "shared"),
          "settlement", Set.of("currency", "pricing", "shared"),
          "reporting", Set.of("shared"),
          "shared", Set.of());

  private ArchitectureRules() {}

  static List<ArchRule> allFor(String rootPackage) {
    return List.of(
        slices().matching(rootPackage + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true),
        classes()
            .that()
            .resideInAPackage(rootPackage + "..")
            .should(obeyModuleBoundaries(rootPackage))
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(rootPackage + "..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web..", "jakarta.persistence..", "com.fasterxml.jackson..")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(rootPackage + "..api..")
            .and()
            .resideOutsideOfPackage(rootPackage + ".reporting.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(rootPackage + "..persistence..")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(rootPackage + "..api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(rootPackage + "..domain..")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(rootPackage + "..service..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(rootPackage + "..api..", rootPackage + "..persistence..")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(rootPackage + "..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                rootPackage + "..api..",
                rootPackage + "..service..",
                rootPackage + "..persistence..")
            .allowEmptyShould(true),
        noClasses()
            .that()
            .resideInAPackage(rootPackage + "..persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(rootPackage + "..api..", rootPackage + "..service..")
            .allowEmptyShould(true));
  }

  static void checkAll(JavaClasses classes, String rootPackage) {
    allFor(rootPackage).forEach(rule -> rule.check(classes));
  }

  private static ArchCondition<JavaClass> obeyModuleBoundaries(String rootPackage) {
    return new ArchCondition<>("obey the approved module dependency matrix and public contracts") {
      @Override
      public void check(JavaClass source, ConditionEvents events) {
        var sourceModule = moduleOf(source, rootPackage);
        if (sourceModule == null) {
          if (source.getPackageName().startsWith(rootPackage + ".")) {
            events.add(
                SimpleConditionEvent.violated(
                    source,
                    source.getName() + " is outside the approved top-level module set " + MODULES));
          }
          return;
        }

        for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
          var target = dependency.getTargetClass();
          var targetModule = moduleOf(target, rootPackage);
          if (targetModule == null) {
            if (target.getPackageName().startsWith(rootPackage + ".")) {
              events.add(
                  SimpleConditionEvent.violated(
                      dependency,
                      source.getName()
                          + " depends on a type outside the approved top-level module set: "
                          + target.getName()));
            }
            continue;
          }
          if (sourceModule.equals(targetModule)) {
            continue;
          }

          var allowedModule =
              ALLOWED_MODULE_DEPENDENCIES
                  .getOrDefault(sourceModule, Set.of())
                  .contains(targetModule);
          var publicContract = isPublicContract(target, rootPackage, targetModule);
          if (!allowedModule
              || !publicContract
              || !target
                  .getModifiers()
                  .contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
            events.add(
                SimpleConditionEvent.violated(
                    dependency,
                    source.getName()
                        + " depends on non-public or forbidden module type "
                        + target.getName()));
          }
        }
      }
    };
  }

  private static boolean isPublicContract(
      JavaClass target, String rootPackage, String targetModule) {
    var modulePackage = rootPackage + "." + targetModule;
    var packageName = target.getPackageName();
    return packageName.equals(modulePackage + ".api")
        || packageName.startsWith(modulePackage + ".api.")
        || packageName.equals(modulePackage + ".domain.port")
        || packageName.startsWith(modulePackage + ".domain.port.");
  }

  private static String moduleOf(JavaClass type, String rootPackage) {
    var prefix = rootPackage + ".";
    if (!type.getPackageName().startsWith(prefix)) {
      return null;
    }
    var remainder = type.getPackageName().substring(prefix.length());
    var separator = remainder.indexOf('.');
    var module = separator < 0 ? remainder : remainder.substring(0, separator);
    return MODULES.contains(module) ? module : null;
  }
}
