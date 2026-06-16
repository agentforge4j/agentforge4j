// SPDX-License-Identifier: Apache-2.0
/**
 * Shipped workflow bundle resources and classpath locators for built-in demonstration bundles.
 *
 * <p>Exposes helpers to resolve {@code shipped-workflows} entries (indexes, {@code workflow.json},
 * agents) so {@code agentforge4j.config.loader} and applications can load curated examples without
 * ad hoc resource paths. Pure resource and locator concerns—no execution logic.
 */
module agentforge4j.workflows {
  requires agentforge4j.util;
  exports com.agentforge4j.workflows;
}
