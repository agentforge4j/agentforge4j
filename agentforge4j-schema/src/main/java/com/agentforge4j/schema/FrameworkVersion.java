// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Exposes the running AgentForge4j framework version.
 *
 * <p>The value is read once from a Maven-filtered {@code agentforge4j-version.properties} resource
 * bundled in this module, whose {@code agentforge4j.version} key is substituted with the reactor
 * version at build time. The catalog compatibility gate compares a shipped catalog's declared
 * {@code minimumAgentForge4jVersion} / {@code maximumAgentForge4jVersion} bounds against this value.
 *
 * <p>The resource lives at the class-path root (not inside a package directory), so the read is a
 * same-module classpath lookup that needs no module {@code opens}.
 */
public final class FrameworkVersion {

  private static final String RESOURCE = "/agentforge4j-version.properties";
  private static final String VERSION_KEY = "agentforge4j.version";
  private static final String VERSION = loadVersion();

  private FrameworkVersion() {
  }

  /**
   * Returns the running framework version, e.g. {@code 0.0.1-SNAPSHOT}.
   *
   * @return the framework version string; never blank
   */
  public static String current() {
    return VERSION;
  }

  private static String loadVersion() {
    try (InputStream stream = FrameworkVersion.class.getResourceAsStream(RESOURCE)) {
      Validate.notNull(stream, () -> new IllegalStateException(
          "Missing framework version resource: %s".formatted(RESOURCE)));
      Properties properties = new Properties();
      properties.load(stream);
      return Validate.notBlank(properties.getProperty(VERSION_KEY),
          "Framework version property '%s' must not be blank".formatted(VERSION_KEY));
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to read framework version resource: %s".formatted(RESOURCE), exception);
    }
  }
}
