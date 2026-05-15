package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowFileRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InMemoryRuntimePersistenceAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(InMemoryRuntimePersistenceAutoConfiguration.class));

  @Test
  void registersInMemoryRepositoriesWhenNoDataSourceBean() {
    runner.run(ctx -> {
      assertThat(ctx.getStartupFailure()).isNull();
      assertThat(ctx).hasSingleBean(WorkflowStateRepository.class);
      assertThat(ctx).hasSingleBean(WorkflowEventLog.class);
      assertThat(ctx).hasSingleBean(WorkflowFileRepository.class);
    });
  }
}
