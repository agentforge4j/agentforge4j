// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;

import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowFileRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

  @Test
  void backsOffInMemoryRepositoriesWhenDataSourceBeanPresent() {
    runner.withUserConfiguration(DataSourceConfiguration.class)
        .run(ctx -> {
          assertThat(ctx.getStartupFailure()).isNull();
          assertThat(ctx).hasSingleBean(DataSource.class);
          assertThat(ctx).doesNotHaveBean(WorkflowStateRepository.class);
          assertThat(ctx).doesNotHaveBean(WorkflowEventLog.class);
          assertThat(ctx).doesNotHaveBean(WorkflowFileRepository.class);
        });
  }

  @Configuration
  static class DataSourceConfiguration {

    @Bean
    DataSource dataSource() {
      return mock(DataSource.class);
    }
  }
}
