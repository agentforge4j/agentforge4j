// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.loader;

import com.agentforge4j.bootstrap.AgentForge4jBootstrap;
import com.agentforge4j.core.exception.DuplicateAgentIdException;
import com.agentforge4j.core.exception.UnresolvedAgentReferenceException;
import com.agentforge4j.verification.support.Fixtures;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Black-box verification of the config-loader's fail-closed negative surface, driven through the real
 * production loaders via {@link AgentForge4jBootstrap#build()}. {@code build()} wraps every loader
 * {@link RuntimeException} in an {@link IllegalStateException} ("Failed to load AgentForge4j
 * configuration"), so each case asserts the wrapper plus the exact typed cause and a message fragment
 * — a regression that swallows or reclassifies a load failure is caught.
 */
class LoaderNegativeTest {

  @Test
  void duplicateAgentIdAcrossSourcesIsRejected() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withAgentsDir(Fixtures.dir("/fixtures/loader/dup-agent/agents"))
        .withWorkflowsDir(Fixtures.dir("/fixtures/loader/dup-agent/workflows"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(DuplicateAgentIdException.class)
        .hasMessageContaining("Duplicate agent id(s):");
  }

  // duplicateWorkflowIdAcrossSourcesIsRejected (filesystem-vs-shipped collision) needs the real
  // shipped catalog, which no longer ships in this reactor; it is recreated in the catalog module in
  // Phase 2. The duplicate-workflow-id rejection mechanism itself is covered by
  // AgentForgeLoaderTest#duplicate* and FileSystemWorkflowLoaderTest in config-loader.

  @Test
  void unresolvedAgentReferenceIsRejected() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withWorkflowsDir(Fixtures.dir("/fixtures/loader/unresolved-ref/workflows"))
        .withLoadShippedWorkflows(false)
        .build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(UnresolvedAgentReferenceException.class)
        .hasMessageContaining("Unresolved agent reference(s):");
  }

  @Test
  void blankAgentVersionIsRejected() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withAgentsDir(Fixtures.dir("/fixtures/loader/blank-version/agents"))
        .withLoadShippedWorkflows(false)
        .withLoadShippedAgents(false)
        .build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void malformedWorkflowJsonSurfacesAsUncheckedIoException() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withWorkflowsDir(Fixtures.dir("/fixtures/loader/malformed-json/workflows"))
        .withLoadShippedWorkflows(false)
        .build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Failed to parse workflow file");
  }

  @Test
  void workflowIdMismatchingBundleDirIsRejected() {
    assertThatThrownBy(() -> AgentForge4jBootstrap.defaults()
        .withLlmClientResolver(Fixtures.noOpLlmResolver())
        .withWorkflowsDir(Fixtures.dir("/fixtures/loader/id-mismatch/workflows"))
        .withLoadShippedWorkflows(false)
        .build())
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match bundle directory name");
  }
}
