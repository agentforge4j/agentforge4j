// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter;

import com.agentforge4j.core.workflow.event.WorkflowEventLog;
import com.agentforge4j.core.workflow.repository.WorkflowFileRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowFileRepository;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * In-memory run state, event log, and file metadata when no {@link DataSource} is configured.
 *
 * <p>When a JPA persistence module and datasource are present, {@link WorkflowStateRepository},
 * {@link WorkflowEventLog}, and {@link WorkflowFileRepository} beans come from that module
 * instead.
 */
@AutoConfiguration(after = {BootstrapAutoConfiguration.class, DataSourceAutoConfiguration.class})
@ConditionalOnMissingBean(DataSource.class)
public class InMemoryRuntimePersistenceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WorkflowStateRepository workflowStateRepository() {
    return new InMemoryWorkflowStateRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowEventLog workflowEventLog() {
    return new InMemoryWorkflowEventLog();
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowFileRepository workflowFileRepository() {
    return new InMemoryWorkflowFileRepository();
  }
}
