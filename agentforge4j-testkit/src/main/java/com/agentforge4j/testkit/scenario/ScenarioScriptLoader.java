// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a scenario's fake-llm script into a {@link FakeScript} from a JSON string, a filesystem
 * path, or a classpath resource. The catalog scenario {@code script.json} is the fake-llm script
 * schema verbatim, so it is parsed directly by {@link FakeScriptParser} with no adapter.
 */
public final class ScenarioScriptLoader {

  private final FakeScriptParser parser;

  /**
   * Creates a loader with a default {@link FakeScriptParser}.
   */
  public ScenarioScriptLoader() {
    this(new FakeScriptParser());
  }

  /**
   * Creates a loader over the given parser.
   *
   * @param parser the script parser; must not be {@code null}
   */
  public ScenarioScriptLoader(FakeScriptParser parser) {
    this.parser = Validate.notNull(parser, "parser must not be null");
  }

  /**
   * Parses a script from a JSON string.
   *
   * @param json the script JSON; must not be blank
   *
   * @return the parsed script
   * @throws IllegalArgumentException if the JSON is malformed or violates the script schema
   */
  public FakeScript fromJson(String json) {
    return parser.parse(Validate.notBlank(json, "json must not be blank"));
  }

  /**
   * Reads and parses a script from a filesystem path.
   *
   * @param path the script file; must not be {@code null}
   *
   * @return the parsed script
   */
  public FakeScript fromPath(Path path) {
    Validate.notNull(path, "path must not be null");
    try {
      return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read fake script: " + path, e);
    }
  }

  /**
   * Reads and parses a script from a classpath resource.
   *
   * @param resource the absolute classpath resource path (e.g. {@code /scenarios/x/script.json});
   *                 must not be blank
   *
   * @return the parsed script
   */
  public FakeScript fromClasspath(String resource) {
    Validate.notBlank(resource, "resource must not be blank");
    try (InputStream stream = ScenarioScriptLoader.class.getResourceAsStream(resource)) {
      Validate.notNull(stream, "Fake script resource not found on classpath: " + resource);
      return fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read fake script resource: " + resource, e);
    }
  }
}
