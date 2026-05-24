package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Immutable facade exposing the assembled AgentForge4j runtime and loaded configuration. Construct
 * via {@link AgentForge4jBootstrap#defaults()}.
 *
 * <p>For full runtime control (state queries, input submission, approval, cancellation),
 * use {@link #runtime()}.
 */
public final class AgentForge4j {

  private final WorkflowRuntime runtime;
  private final LoadedConfiguration loadedConfiguration;

  /**
   * Package-private construction from {@link AgentForge4jBootstrap.Builder#build()}.
   *
   * @param runtime             assembled workflow runtime; must not be {@code null}
   * @param loadedConfiguration loaded agent and workflow definitions; must not be {@code null}
   */
  AgentForge4j(WorkflowRuntime runtime, LoadedConfiguration loadedConfiguration) {
    this.runtime = Validate.notNull(runtime, "runtime should not be null");
    this.loadedConfiguration = Validate.notNull(loadedConfiguration,
        "loadedConfiguration should not be null");
  }

  /**
   * Starts a workflow run for the given workflow id and returns the run id.
   *
   * @param workflowId the id of the workflow to start; must not be blank
   * @return the new run id; never {@code null}
   */
  public String start(String workflowId) {
    Validate.notBlank(workflowId, "workflowId should not be blank");
    return runtime.start(workflowId);
  }

  /**
   * Returns an immutable view of all loaded workflow definitions.
   *
   * @return unmodifiable list; never {@code null}
   */
  public List<WorkflowDefinition> workflows() {
    return List.copyOf(loadedConfiguration.workflows().values());
  }

  /**
   * Returns an immutable view of all loaded agent definitions.
   *
   * @return unmodifiable list; never {@code null}
   */
  public List<AgentDefinition> agents() {
    return List.copyOf(loadedConfiguration.agents().values());
  }

  /**
   * Returns the full runtime interface for operational use (state queries, input submission,
   * approval, cancellation, retry).
   *
   * @return runtime; never {@code null}
   */
  public WorkflowRuntime runtime() {
    return runtime;
  }

  // Phase 7: components() accessor added here
}
