// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.bootstrap.AgentForge4j;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.llm.fake.FakeResponse;
import com.agentforge4j.llm.fake.FakeResponseSource;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptKey;
import com.agentforge4j.llm.fake.StaticFakeResponseSource;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import com.agentforge4j.starter.llmclient.fake.FakeProviderAutoConfiguration;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tier 6 — Spring Boot starter verification. Boots the starter auto-configuration through an
 * {@link ApplicationContextRunner} and proves the Spring wiring assembles a working {@link AgentForge4j}
 * facade from {@code agentforge4j.*} properties, then drives a one-step agent workflow to completion via
 * the deterministic fake provider. Lives in its own module because it is the only tier that touches
 * Spring (the Spring-free oss-verification suite forbids it).
 *
 * <p>The fake is activated the supported way — {@code agentforge4j.llm.fake.enabled=true} with the
 * backing module on the test classpath — and scripted by overriding the auto-configuration's
 * {@link FakeResponseSource} (declared {@code @ConditionalOnMissingBean}) with a run-agnostic
 * {@link StaticFakeResponseSource}, the same seam the testkit harness uses.
 */
class StarterVerificationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          FakeProviderAutoConfiguration.class, BootstrapAutoConfiguration.class))
      .withPropertyValues(
          "agentforge4j.agents-path=" + dir("/fixtures/starter/agents"),
          "agentforge4j.workflows-path=" + dir("/fixtures/starter/workflows"));

  @Test
  void starterAssemblesFacadeAndRunsWorkflowEndToEnd() {
    fakeEnabled().run(ctx -> {
      assertThat(ctx).hasSingleBean(AgentForge4j.class);
      AgentForge4j af = ctx.getBean(AgentForge4j.class);
      WorkflowRuntime runtime = af.runtime();
      String runId = runtime.start("starter-smoke");
      assertThat(runtime.getState(runId).getStatus())
          .as("the Spring-wired facade must run the workflow to completion via the fake provider")
          .isEqualTo(WorkflowStatus.COMPLETED);
    });
  }

  @Test
  void bootstrapComponentsAreExposedThroughTheFacadeBean() {
    fakeEnabled().run(ctx -> {
      assertThat(ctx).hasSingleBean(AgentForge4j.class);
      AgentForge4j af = ctx.getBean(AgentForge4j.class);
      assertThat(af.components()).as("assembled components must be exposed").isNotNull();
      assertThat(af.runtime()).as("the runtime must be reachable from the facade").isNotNull();
    });
  }

  @Test
  void fakeProviderIsNotWiredWhenDisabled() {
    // No agentforge4j.llm.fake.enabled=true → FakeProviderAutoConfiguration backs off (its
    // @ConditionalOnProperty gate), the resolver has no client, and the agent step fails for lack of a
    // provider. The facade still assembles — only the provider wiring is gated.
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(AgentForge4j.class);
      AgentForge4j af = ctx.getBean(AgentForge4j.class);
      WorkflowRuntime runtime = af.runtime();
      String runId = runtime.start("starter-smoke");
      assertThat(runtime.getState(runId).getStatus())
          .as("with the fake provider gated off, no provider is available and the run fails")
          .isEqualTo(WorkflowStatus.FAILED);
    });
  }

  private ApplicationContextRunner fakeEnabled() {
    return runner
        .withPropertyValues("agentforge4j.llm.fake.enabled=true")
        .withBean(FakeResponseSource.class, () -> new StaticFakeResponseSource(script()));
  }

  private static FakeScript script() {
    return new FakeScript(1, Map.of(
        new FakeScriptKey("starter-smoke", "run", "fake-agent", 0),
        new FakeResponse("[{\"type\":\"COMPLETE\"}]", null)));
  }

  private static String dir(String classpathDir) {
    URL url = StarterVerificationTest.class.getResource(classpathDir);
    if (url == null) {
      throw new IllegalStateException("Missing fixture directory on the classpath: " + classpathDir);
    }
    try {
      return Paths.get(url.toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Unresolvable fixture URL: " + url, e);
    }
  }
}
