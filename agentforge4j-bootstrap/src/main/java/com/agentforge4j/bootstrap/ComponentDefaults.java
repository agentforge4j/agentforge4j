package com.agentforge4j.bootstrap;

import com.agentforge4j.config.loader.LoadedConfiguration;
import com.agentforge4j.config.loader.repository.InMemoryAgentRepository;
import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.agent.AgentRepository;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.LocalFileSink;
import java.nio.file.Path;

/**
 * Constructs default component instances for {@link AgentForge4jBootstrap}. Internal — not part of
 * the public API.
 */
final class ComponentDefaults {

  private static final System.Logger LOGGER =
      System.getLogger(ComponentDefaults.class.getName());

  private ComponentDefaults() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Returns the default in-memory agent repository populated from the loaded configuration.
   *
   * @param loadedConfiguration source of agent definitions; must not be {@code null}
   * @return populated repository; never {@code null}
   */
  static AgentRepository agentRepository(LoadedConfiguration loadedConfiguration) {
    return new InMemoryAgentRepository(loadedConfiguration.agents());
  }

  /**
   * Returns the default in-memory workflow repository populated from the loaded configuration.
   *
   * @param loadedConfiguration source of workflow definitions; must not be {@code null}
   * @return populated repository; never {@code null}
   */
  static WorkflowRepository workflowRepository(LoadedConfiguration loadedConfiguration) {
    return new InMemoryWorkflowRepository(loadedConfiguration.workflows());
  }

  /**
   * Returns the effective {@link FileSink}. Uses {@code fileSinkPath} when set, otherwise returns
   * {@link FileSink#NO_OP_FILE_SINK} and logs a WARNING.
   *
   * @param fileSinkPath optional base directory for file output; {@code null} for no-op
   * @return effective file sink; never {@code null}
   */
  static FileSink fileSink(Path fileSinkPath) {
    if (fileSinkPath != null) {
      return new LocalFileSink(fileSinkPath);
    }
    LOGGER.log(System.Logger.Level.WARNING,
        """
            FileSink is no-op; CreateFileCommand outputs will be discarded. \
            Override with .withFileSink(new LocalFileSink(Path.of(...))).""");
    return FileSink.NO_OP_FILE_SINK;
  }
}
