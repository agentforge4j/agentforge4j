// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.starter.llmclient.fake;

import com.agentforge4j.llm.fake.FakeConfiguration;
import com.agentforge4j.llm.fake.FakeResponseSource;
import com.agentforge4j.llm.fake.FakeRunLifecycle;
import com.agentforge4j.llm.fake.RegistryFakeResponseSource;
import com.agentforge4j.starter.BootstrapAutoConfiguration;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Enables the fake scripted provider when {@code agentforge4j.llm.fake.enabled=true} once the backing module is on the
 * classpath.
 *
 * <p>Wires three beans that share one {@link FakeRunLifecycle} store: the lifecycle itself (the
 * registration API the test runner / demo flow injects to {@code register}/{@code deregister} scripts per run), a
 * {@link RegistryFakeResponseSource} over it, and a {@link FakeConfiguration} carrying that source — contributed
 * (before {@link BootstrapAutoConfiguration}) to the {@code LlmClientConfiguration} list so the resolver builds a fake
 * client bound to the same store. The source and lifecycle are {@code @ConditionalOnMissingBean}, so an application may
 * supply its own (for example a {@code StaticFakeResponseSource}).
 */
@AutoConfiguration(before = BootstrapAutoConfiguration.class)
@EnableConfigurationProperties(FakeLlmClientProperties.class)
@ConditionalOnClass(FakeConfiguration.class)
@ConditionalOnProperty(name = "agentforge4j.llm.fake.enabled", havingValue = "true")
public class FakeProviderAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  FakeRunLifecycle fakeRunLifecycle(FakeLlmClientProperties properties, ObjectProvider<Clock> clock) {
    return new FakeRunLifecycle(clock.getIfAvailable(Clock::systemUTC), properties.ttl(), properties.maxRuns());
  }

  @Bean
  @ConditionalOnMissingBean
  FakeResponseSource fakeResponseSource(FakeRunLifecycle lifecycle) {
    return new RegistryFakeResponseSource(lifecycle);
  }

  @Bean
  FakeConfiguration fakeConfiguration(FakeResponseSource responseSource) {
    return new FakeConfiguration(responseSource);
  }
}
