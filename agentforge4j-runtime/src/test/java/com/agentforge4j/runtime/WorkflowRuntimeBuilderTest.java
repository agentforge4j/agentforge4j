package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.integrations.NoOpIntegrationRegistry;
import com.agentforge4j.runtime.command.FileSink;
import com.agentforge4j.runtime.command.ShellCommandRunner;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates {@link WorkflowRuntimeBuilder} assembly requirements.
 */
class WorkflowRuntimeBuilderTest {

  @Test
  void build_withoutAgentInvoker_failsFast() {
    assertThatThrownBy(() -> new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Collections.emptyMap()))
        .workflowStateRepository(new InMemoryWorkflowStateRepository())
        .workflowEventLog(new InMemoryWorkflowEventLog())
        .clock(Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC))
        .integrationRegistry(NoOpIntegrationRegistry.INSTANCE)
        .fileSink(FileSink.NO_OP_FILE_SINK)
        .shellCommandRunner(ShellCommandRunner.NO_OP_SHELL_COMMAND_RUNNER)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentInvoker is required");
  }
}
