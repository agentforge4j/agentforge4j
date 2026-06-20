// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Guards that the OSS verification suite stays a pure black-box behavioural suite: no Spring and no
 * persistence (JPA) dependencies. Cloud and platform packages do not exist in the OSS tree, so this
 * documents the intent that the suite never grows toward them.
 */
class VerificationNoSpringGuardTest {

  @Test
  void verificationDependsOnNoSpringOrPersistence() {
    JavaClasses classes = new ClassFileImporter().importPackages("com.agentforge4j.verification");
    noClasses().should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "javax.persistence..")
        .because("the OSS verification suite must stay Spring-free and database-free")
        .check(classes);
  }
}
