package com.srm.creditengine.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  @Test
  void production_code_obeys_architecture_rules() {
    var productionClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.srm.creditengine");

    ArchitectureRules.checkAll(productionClasses, "com.srm.creditengine");
  }
}
