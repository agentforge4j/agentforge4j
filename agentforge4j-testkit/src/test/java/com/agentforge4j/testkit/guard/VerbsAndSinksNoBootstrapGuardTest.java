// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Guard: the assertion, capture, and scenario layers never reference {@code bootstrap}, which is
 * reachable only through the narrow harness adapter (design §E.4 guard 2). This keeps the verbs and
 * sinks assembly-agnostic.
 */
class VerbsAndSinksNoBootstrapGuardTest {

  @Test
  void verbsAndSinksDoNotReferenceBootstrap() {
    JavaClasses classes = new ClassFileImporter().importPackages("com.agentforge4j.testkit");

    noClasses()
        .that().resideInAnyPackage(
            "com.agentforge4j.testkit.assertion..",
            "com.agentforge4j.testkit.capture..",
            "com.agentforge4j.testkit.scenario..")
        .should().dependOnClassesThat().resideInAnyPackage("com.agentforge4j.bootstrap..")
        .because("verbs and sinks must stay assembly-agnostic; bootstrap is reachable only via the "
            + "harness adapter")
        .check(classes);
  }
}
