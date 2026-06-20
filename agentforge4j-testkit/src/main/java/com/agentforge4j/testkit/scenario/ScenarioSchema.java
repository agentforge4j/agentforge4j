// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Accessor for the JSON-schema that defines the catalog scenario {@code expected-result.json}
 * contract (the workflow to drive, the ordered human gate responses, and the assertions to project
 * onto a run). The schema is shipped as a classpath resource alongside this class inside the testkit
 * artifact.
 *
 * <p>The resource is loaded by a class in its own module, so it resolves whether the testkit is read
 * as a named JPMS module or flattened onto the classpath — and an external catalog repo consuming
 * the published testkit artifact reaches it through this API rather than reaching into testkit source
 * paths.
 */
public final class ScenarioSchema {

  /** Resource name of the scenario schema, relative to this class's package. */
  public static final String RESOURCE_NAME = "scenario.schema.json";

  private ScenarioSchema() {
  }

  /**
   * Opens the scenario schema resource as a stream.
   *
   * @return an open stream over the schema JSON; the caller closes it
   * @throws IllegalStateException if the schema resource is absent from the testkit artifact
   */
  public static InputStream open() {
    InputStream stream = ScenarioSchema.class.getResourceAsStream(RESOURCE_NAME);
    return Validate.notNull(stream,
        () -> new IllegalStateException(
            "Scenario schema resource '%s' is missing from the testkit artifact"
                .formatted(RESOURCE_NAME)));
  }

  /**
   * Reads the scenario schema as a UTF-8 string.
   *
   * @return the schema JSON text
   */
  public static String json() {
    try (InputStream stream = open()) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read scenario schema resource", e);
    }
  }
}
