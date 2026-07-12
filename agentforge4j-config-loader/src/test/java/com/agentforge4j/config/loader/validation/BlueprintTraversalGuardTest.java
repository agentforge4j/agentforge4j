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
 *
 * <p>{@code com.agentforge4j.core.workflow.WorkflowComplexityAnalyzer} is also sanctioned: it performs
 * its own bounded recursive descent (rather than delegating to {@link WorkflowTreeWalker}) because it
 * must carry loop-expansion factors and nesting depth down the tree, context the shared walker's
 * {@code (step, scope)} visitor signature does not expose. Its descent independently mirrors the
 * shared walker's depth guard, so it is not vulnerable to CL-1's bug shape — it is named here by fully
 * qualified string, not {@code Class<?>} literal, because it does not yet exist on this branch (it
 * lands on {@code main} via a separate, unrelated PR); once this branch is rebased past that point,
 * prefer switching this entry to {@code WorkflowComplexityAnalyzer.class} for compile-time safety.
 */
class BlueprintTraversalGuardTest {

  private static final JavaClasses CLASSES = new ClassFileImporter()
      .importPackages("com.agentforge4j.core.workflow", "com.agentforge4j.config.loader");

  private static final DescribedPredicate<JavaClass> NOT_SANCTIONED =
      DescribedPredicate.not(belongsToClassOrItsNestedClasses(WorkflowTreeWalker.class.getName()))
          .and(DescribedPredicate.not(
              belongsToClassOrItsNestedClasses(ReachableStepGraph.class.getName())))
          .and(DescribedPredicate.not(belongsToClassOrItsNestedClasses(
              "com.agentforge4j.core.workflow.WorkflowComplexityAnalyzer")));

  /**
   * Matches the class named {@code sanctionedFullyQualifiedName} itself and any of its nested classes
   * (e.g. a private inner {@code Traversal} helper) — ArchUnit's {@code equivalentTo} only matches the
   * exact class, which would miss the actual traversal logic when it lives in a nested class. Takes a
   * fully qualified name rather than a {@code Class<?>} literal so a carve-out can be declared for a
   * class that does not yet exist on this branch without a hard compile-time dependency on it.
   */
  private static DescribedPredicate<JavaClass> belongsToClassOrItsNestedClasses(
      String sanctionedFullyQualifiedName) {
    return new DescribedPredicate<>("belongs to " + sanctionedFullyQualifiedName) {
      @Override
      public boolean test(JavaClass javaClass) {
        return javaClass.getFullName().equals(sanctionedFullyQualifiedName)
            || javaClass.getFullName().startsWith(sanctionedFullyQualifiedName + "$");
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
