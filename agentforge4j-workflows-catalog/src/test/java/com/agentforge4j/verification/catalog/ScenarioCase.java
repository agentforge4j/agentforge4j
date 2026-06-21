// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

/**
 * One discovered catalog scenario on disk: its directory name, the raw fake-llm {@code script.json},
 * the parsed {@code expected-result.json}, and whether a {@code README.md} accompanies it.
 *
 * @param name          scenario directory name (also the JUnit display name)
 * @param scriptJson    raw fake-llm script JSON, parsed lazily by the runner
 * @param expected      the parsed expected-result bundle
 * @param readmePresent whether a {@code README.md} is present (conformance gate)
 */
public record ScenarioCase(String name, String scriptJson, ExpectedResult expected,
    boolean readmePresent) {

  @Override
  public String toString() {
    return name;
  }
}
