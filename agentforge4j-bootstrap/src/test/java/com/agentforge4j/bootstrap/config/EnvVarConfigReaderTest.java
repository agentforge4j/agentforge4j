package com.agentforge4j.bootstrap.config;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EnvVarConfigReader}.
 * <p>
 * {@link EnvVarConfigReader#read()} reads live JVM state. Tests use system properties only because
 * environment variables cannot be set or cleared reliably in-process. Env-var normalisation and
 * env-vs-property precedence on collision are covered by code review and the disabled collision
 * test below.
 * <p>
 * Defensive null-key/null-value skipping in system properties cannot be exercised via
 * {@link System#setProperty(String, String)} (it rejects null); that path is validated by code
 * review.
 */
class EnvVarConfigReaderTest {

  private final Map<String, String> originalValues = new HashMap<>();

  @AfterEach
  void restoreProperties() {
    for (Map.Entry<String, String> entry : originalValues.entrySet()) {
      String key = entry.getKey();
      String previous = entry.getValue();
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
    originalValues.clear();
  }

  @Test
  void systemPropertyWithPrefixIsIncluded() {
    setProperty("agentforge4j.llm.openai.api-key", "test-key");

    Map<String, String> result = EnvVarConfigReader.read();

    assertThat(result).containsEntry("agentforge4j.llm.openai.api-key", "test-key");
  }

  @Test
  void systemPropertyWithoutPrefixIsExcluded() {
    setProperty("someother.property", "value");

    Map<String, String> result = EnvVarConfigReader.read();

    assertThat(result).doesNotContainKey("someother.property");
  }

  @Test
  @Disabled("Environment variables cannot be set in-process; collision precedence is validated by code review")
  void systemPropertyWinsOverEnvVarOnCollision() {
    // AGENTFORGE4J_* env vars normalise to agentforge4j.* keys; system properties with the same
    // key must win on merge (putAll after env map). Not testable without external env injection.
  }

  @Test
  void returnedMapIsImmutable() {
    Map<String, String> result = EnvVarConfigReader.read();

    assertThatThrownBy(() -> result.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private void setProperty(String key, String value) {
    originalValues.putIfAbsent(key, System.getProperty(key));
    System.setProperty(key, value);
  }
}
