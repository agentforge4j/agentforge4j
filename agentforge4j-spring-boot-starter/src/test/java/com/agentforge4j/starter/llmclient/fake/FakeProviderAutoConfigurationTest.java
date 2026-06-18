// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.fake;

import com.agentforge4j.llm.LlmClientFactoryContext;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationIdentity;
import com.agentforge4j.llm.fake.FakeConfiguration;
import com.agentforge4j.llm.fake.FakeLlmClientFactory;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeResponseSource;
import com.agentforge4j.llm.fake.FakeRunLifecycle;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FakeProviderAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(FakeProviderAutoConfiguration.class));

  @Test
  void skipsWhenNotEnabled() {
    runner.run(ctx -> {
      assertThat(ctx).doesNotHaveBean(FakeConfiguration.class);
      assertThat(ctx).doesNotHaveBean(FakeRunLifecycle.class);
      assertThat(ctx).doesNotHaveBean(FakeResponseSource.class);
    });
  }

  @Test
  void registersBeansWhenEnabled() {
    runner.withPropertyValues("agentforge4j.llm.fake.enabled=true").run(ctx -> {
      assertThat(ctx).hasSingleBean(FakeConfiguration.class);
      assertThat(ctx).hasSingleBean(FakeRunLifecycle.class);
      assertThat(ctx).hasSingleBean(FakeResponseSource.class);
      assertThat(ctx.getBean(FakeConfiguration.class).getProviderName()).isEqualTo("fake");
    });
  }

  @Test
  void lifecycleBeanAndConfiguration_shareOneStore() {
    runner.withPropertyValues("agentforge4j.llm.fake.enabled=true").run(ctx -> {
      FakeRunLifecycle lifecycle = ctx.getBean(FakeRunLifecycle.class);
      FakeConfiguration configuration = ctx.getBean(FakeConfiguration.class);

      // Register against the lifecycle bean; resolve through a client built from the configuration.
      lifecycle.register("run-1", new FakeScript(1, Map.of(
          new FakeScriptKey("wf", "s1", "a1", 0), new FakeResponse("scripted", null))));
      LlmClient client = new FakeLlmClientFactory().create(new LlmClientFactoryContext(
          new ObjectMapper(), configuration, reference -> new LlmSecret("unused")));

      LlmExecutionResponse response = client.execute(new LlmExecutionRequest(
          "fake", null, "system", "user", null, null,
          new LlmInvocationIdentity("wf", "run-1", "s1", "a1")));

      assertThat(response.text()).isEqualTo("scripted");
    });
  }

  @Test
  void maxRunsProperty_propagatesToLifecycleBean() {
    runner.withPropertyValues(
            "agentforge4j.llm.fake.enabled=true",
            "agentforge4j.llm.fake.max-runs=1")
        .run(ctx -> {
          FakeRunLifecycle lifecycle = ctx.getBean(FakeRunLifecycle.class);
          FakeScript script = new FakeScript(1, Map.of(
              new FakeScriptKey("wf", "s1", "a1", 0), new FakeResponse("x", null)));

          lifecycle.register("run-a", script);
          lifecycle.register("run-b", script); // exceeds cap of 1

          assertThat(lifecycle.isRegistered("run-a")).isFalse();
          assertThat(lifecycle.isRegistered("run-b")).isTrue();
        });
  }
}
