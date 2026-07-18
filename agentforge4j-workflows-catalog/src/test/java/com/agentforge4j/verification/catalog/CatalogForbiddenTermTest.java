// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import com.agentforge4j.testkit.assertion.ForbiddenTermScanner;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Applies the generic {@link ForbiddenTermScanner} (design §13) to the <em>entire</em> shipped
 * catalog — every workflow bundle under {@code /shipped-workflows} (including verification
 * scenarios) and every reusable agent under {@code /shipped-agents}: no currency, pricing,
 * billing, or credit-adjacent term may appear in a shipped OSS prompt, workflow definition,
 * artifact, or README, regardless of whether it appears inside a "never do X" instruction — a
 * substring scan cannot tell intent apart from violation, so the resources themselves must not
 * carry the forbidden words at all.
 *
 * <p>Scanning the roots wholesale means a newly added workflow or agent bundle is covered
 * automatically — the guard scales with the catalog instead of being pinned to one workflow.
 */
class CatalogForbiddenTermTest {

  private static final Set<String> FORBIDDEN_TERMS = Set.of(
      "€", "$", "credit", "credits", "billing", "subscription", "payment", "ledger",
      "entitlement", "reservation", "admission", "pricing", "currency", "changes.md");

  @Test
  void shippedWorkflowCatalogCarriesNoForbiddenTerms() throws URISyntaxException {
    ForbiddenTermScanner.assertNoForbiddenTerms(resourceDirectory("/shipped-workflows"),
        FORBIDDEN_TERMS,
        path -> path.toString().endsWith(".json") || path.toString().endsWith(".md"));
  }

  @Test
  void shippedAgentCatalogCarriesNoForbiddenTerms() throws URISyntaxException {
    ForbiddenTermScanner.assertNoForbiddenTerms(resourceDirectory("/shipped-agents"),
        FORBIDDEN_TERMS,
        path -> path.toString().endsWith(".json") || path.toString().endsWith(".md"));
  }

  private static Path resourceDirectory(String classpathDirectory) throws URISyntaxException {
    return Path.of(CatalogForbiddenTermTest.class.getResource(classpathDirectory).toURI());
  }
}
