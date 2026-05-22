package com.agentforge4j.bootstrap.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code AGENTFORGE4J_*} environment variables and {@code agentforge4j.*}
 * system properties, normalises both to dot-form, and merges them into a single map.
 * System properties win over environment variables on key collision.
 * Internal — not part of the public API.
 */
final class EnvVarConfigReader {

  private static final String ENV_PREFIX = "AGENTFORGE4J_";
  private static final String PROP_PREFIX = "agentforge4j.";

  private EnvVarConfigReader() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Returns a merged map of all AgentForge4j configuration entries found in the
   * environment and system properties, keyed in dot-form
   * (e.g. {@code agentforge4j.llm.openai.api-key}).
   * System properties take precedence over environment variables on collision.
   *
   * @return immutable map of normalised configuration keys to their values; never {@code null}
   */
  static Map<String, String> read() {
    Map<String, String> fromEnv = new HashMap<>();
    for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null) {
        continue;
      }
      if (key.startsWith(ENV_PREFIX)) {
        String normalisedKey = PROP_PREFIX
            + key.substring(ENV_PREFIX.length()).replace('_', '.').toLowerCase(Locale.ROOT);
        fromEnv.put(normalisedKey, value);
      }
    }

    Map<String, String> merged = new HashMap<>(fromEnv);
    for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
      String key = (String) entry.getKey();
      String value = (String) entry.getValue();
      if (key == null || value == null) {
        continue;
      }
      if (key.startsWith(PROP_PREFIX)) {
        merged.put(key, value);
      }
    }
    return Map.copyOf(merged);
  }
}
