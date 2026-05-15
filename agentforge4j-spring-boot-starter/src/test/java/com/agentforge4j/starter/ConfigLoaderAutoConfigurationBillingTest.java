package com.agentforge4j.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderAutoConfigurationBillingTest {

  @Test
  void doesNotRegisterBillingTierProperties() {
    EnableConfigurationProperties annotation =
      ConfigLoaderAutoConfiguration.class.getAnnotation(EnableConfigurationProperties.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).containsExactly(AgentForge4jProperties.class);
  }

  @Test
  void billingTierPropertiesClassIsNotOnStarterClasspath() {
    assertThat(classExists("com.agentforge4j.starter.billing.AgentForge4jBillingTiersProperties")).isFalse();
  }

  private static boolean classExists(String className) {
    try {
      Class.forName(className, false, ConfigLoaderAutoConfigurationBillingTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException ex) {
      return false;
    }
  }
}
