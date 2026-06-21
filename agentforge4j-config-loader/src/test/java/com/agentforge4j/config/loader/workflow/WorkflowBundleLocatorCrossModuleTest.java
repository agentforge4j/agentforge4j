// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;

/**
 * Proves the locator's classloader-based resolution finds catalog-shaped resources that live in a
 * <em>separate</em> module/jar — {@code agentforge4j-workflow-fixtures}, a test-scope dependency
 * providing {@code /test-workflows/}.
 *
 * <p>This is the cross-module mechanism the shipped catalog relies on: the real catalog ships as its
 * own jar, so {@link Class#getResource(String)} (caller-module-relative) would not find it, while
 * {@link ClassLoader#getResource(String)} over the non-encapsulated, hyphenated root does — across
 * both the class path and the module path.
 */
class WorkflowBundleLocatorCrossModuleTest {

  @Test
  void classloaderResolvesResourcesFromSeparateFixtureJar() {
    ClassLoader loader = WorkflowBundleLocator.class.getClassLoader();

    URL index = loader.getResource("test-workflows/index");
    assertThat(index)
        .as("test-workflows/index resolved from the separate workflow-fixtures jar")
        .isNotNull();

    URL workflowJson = loader.getResource("test-workflows/sample.workflow/workflow.json");
    assertThat(workflowJson).isNotNull();
  }
}
