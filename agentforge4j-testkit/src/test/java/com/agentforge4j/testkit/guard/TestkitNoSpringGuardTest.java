// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Guard: the published testkit engine never references Spring (design §E.4 guard 1).
 */
class TestkitNoSpringGuardTest {

  @Test
  void testkitDoesNotReferenceSpring() {
    JavaClasses classes = new ClassFileImporter().importPackages("com.agentforge4j.testkit");

    noClasses().should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
        .because("the published testkit engine must stay Spring-free")
        .check(classes);
  }
}
