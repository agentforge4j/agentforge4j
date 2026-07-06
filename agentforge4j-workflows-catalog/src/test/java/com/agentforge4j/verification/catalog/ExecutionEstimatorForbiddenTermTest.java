// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.testkit.assertion.ForbiddenTermScanner;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Applies the generic {@link ForbiddenTermScanner} (design §13) to the shipped
 * {@code execution-estimator} agent and {@code workflow-execution-estimator} workflow resources: no
 * currency, pricing, billing, or credit-adjacent term may appear in a shipped OSS prompt, workflow
 * definition, or artifact, regardless of whether it appears inside a "never do X" instruction — a
 * substring scan cannot tell intent apart from violation, so the resources themselves must not carry
 * the forbidden words at all.
 */
class ExecutionEstimatorForbiddenTermTest {

  private static final Set<String> FORBIDDEN_TERMS = Set.of(
      "€", "$", "credit", "credits", "billing", "subscription", "payment", "ledger",
      "entitlement", "reservation", "admission", "pricing", "currency", "changes.md");

  @Test
  void shippedAgentResourcesCarryNoForbiddenTerms() throws URISyntaxException {
    Path root = resourceDirectory("/shipped-agents/execution-estimator.agent");
    ForbiddenTermScanner.assertNoForbiddenTerms(root, FORBIDDEN_TERMS,
        path -> path.toString().endsWith(".json") || path.toString().endsWith(".md"));
  }

  @Test
  void shippedWorkflowResourcesCarryNoForbiddenTerms() throws URISyntaxException {
    Path root = resourceDirectory("/shipped-workflows/workflow-execution-estimator.workflow");
    ForbiddenTermScanner.assertNoForbiddenTerms(root, FORBIDDEN_TERMS,
        path -> path.toString().endsWith(".json") || path.toString().endsWith(".md"));
  }

  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(ExecutionEstimatorForbiddenTermTest.class.getResource(classpathDirectory).toURI());
  }
}
