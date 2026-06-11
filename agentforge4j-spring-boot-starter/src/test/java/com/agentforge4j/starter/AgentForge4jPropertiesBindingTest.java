package com.agentforge4j.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class AgentForge4jPropertiesBindingTest {

  @Test
  void bindsFilesystemPathsShippedFlagsAndMaxNestingDepth() {
    MockEnvironment env = new MockEnvironment()
        .withProperty("agentforge4j.agents-path", "/var/agents")
        .withProperty("agentforge4j.workflows-path", "/var/workflows")
        .withProperty("agentforge4j.integrations.dir", "/var/integrations")
        .withProperty("agentforge4j.max-nesting-depth", "42")
        .withProperty("agentforge4j.load-shipped-workflows", "true")
        .withProperty("agentforge4j.load-shipped-agents", "false");

    AgentForge4jProperties props = Binder.get(env)
        .bindOrCreate("agentforge4j", Bindable.of(AgentForge4jProperties.class));

    assertThat(props.agentsPath()).isEqualTo("/var/agents");
    assertThat(props.workflowsPath()).isEqualTo("/var/workflows");
    assertThat(props.integrations().dir()).isEqualTo("/var/integrations");
    assertThat(props.maxNestingDepth()).isEqualTo(42);
    assertThat(props.loadShippedWorkflows()).isTrue();
    assertThat(props.loadShippedAgents()).isFalse();
  }

  @Test
  void omitsMaxNestingDepthWhenPropertyUnset() {
    MockEnvironment env = new MockEnvironment();

    AgentForge4jProperties props = Binder.get(env)
        .bindOrCreate("agentforge4j", Bindable.of(AgentForge4jProperties.class));

    assertThat(props.maxNestingDepth()).isNull();
    assertThat(props.loadShippedWorkflows()).isFalse();
    assertThat(props.loadShippedAgents()).isFalse();
  }
}
