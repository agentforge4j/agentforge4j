// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.agentforge4j.core.workflow.WorkflowTreeWalker;
import com.agentforge4j.core.workflow.reachability.ReachableStepGraph;
import com.agentforge4j.core.workflow.step.blueprint.BlueprintRef;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Guards against reintroducing CL-1's bug shape: a bespoke walk that resolves a
 * {@link BlueprintRef} by hand instead of going through {@link WorkflowTreeWalker} (or
 * {@link ReachableStepGraph}, which has its own independent cycle-safe walk), and therefore has no
 * protection against a self-referencing or mutually-referencing blueprint causing an unbounded
 * recursion.
 *
 * <p>Scoped to {@code agentforge4j-core}'s workflow package and {@code agentforge4j-config-loader}
 * — the two modules this PR's fix touches. A pre-existing, equivalent gap in
 * {@code agentforge4j-runtime} ({@code StepTreeSearcher}, {@code BlueprintExecutor}) is tracked as a
 * separate, standalone follow-up rather than folded into this rule, so this test does not scan that
 * module.
 */
class BlueprintTraversalGuardTest {

  private static final JavaClasses CLASSES = new ClassFileImporter()
      .importPackages("com.agentforge4j.core.workflow", "com.agentforge4j.config.loader");

  private static final DescribedPredicate<JavaClass> NOT_SANCTIONED =
      DescribedPredicate.not(belongsToClassOrItsNestedClasses(WorkflowTreeWalker.class))
          .and(DescribedPredicate.not(belongsToClassOrItsNestedClasses(ReachableStepGraph.class)));

  /**
   * Matches {@code sanctioned} itself and any of its nested classes (e.g. a private inner
   * {@code Traversal} helper) — ArchUnit's {@code equivalentTo} only matches the exact class, which
   * would miss the actual traversal logic when it lives in a nested class.
   */
  private static DescribedPredicate<JavaClass> belongsToClassOrItsNestedClasses(Class<?> sanctioned) {
    String prefix = sanctioned.getName();
    return new DescribedPredicate<>("belongs to " + prefix) {
      @Override
      public boolean test(JavaClass javaClass) {
        return javaClass.getFullName().equals(prefix)
            || javaClass.getFullName().startsWith(prefix + "$");
      }
    };
  }

  @Test
  void onlyWorkflowTreeWalkerAndReachableStepGraphResolveBlueprintRefs() {
    ArchRule rule = noClasses()
        .that(NOT_SANCTIONED)
        .should().callMethod(BlueprintRef.class, "blueprintId")
        .because("resolving a BlueprintRef to its BlueprintDefinition and recursing into its steps "
            + "is exactly the operation that caused CL-1's StackOverflowError; every consumer must "
            + "go through WorkflowTreeWalker's (or ReachableStepGraph's) shared cycle/depth guard "
            + "instead of reimplementing an unguarded resolve-and-recurse by hand");

    rule.check(CLASSES);
  }
}
