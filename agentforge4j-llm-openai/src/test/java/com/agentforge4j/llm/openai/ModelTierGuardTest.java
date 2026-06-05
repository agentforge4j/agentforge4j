package com.agentforge4j.llm.openai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Guards the layering rule that provider modules never depend on the orchestration-level
 * {@code ModelTier} type. Tier resolution belongs to the runtime, not to provider adapters.
 */
class ModelTierGuardTest {

  @Test
  void providerModuleDoesNotDependOnModelTier() {
    JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.agentforge4j.llm.openai");

    noClasses()
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("com.agentforge4j.llm.api.ModelTier")
        .check(classes);
  }
}
