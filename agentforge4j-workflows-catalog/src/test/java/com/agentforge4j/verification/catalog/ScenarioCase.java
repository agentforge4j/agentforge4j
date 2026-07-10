// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

/**
 * One discovered catalog scenario on disk. A workflow folder may own several scenarios; each is a
 * {@code verification/<scenario>/} sub-folder with a fake-llm {@code script.json}, a parsed
 * {@code expected-result.json}, and (required) a {@code README.md}.
 *
 * @param name             display name, {@code <workflowId>/<scenario>} (also the JUnit test name)
 * @param owningWorkflowId id of the shipped workflow folder that owns this scenario
 * @param scriptJson       raw fake-llm script JSON, parsed lazily by the runner
 * @param expected         the parsed expected-result bundle
 * @param readmePresent    whether a {@code README.md} accompanies the scenario (conformance gate)
 */
public record ScenarioCase(String name, String owningWorkflowId, String scriptJson,
    ExpectedResult expected, boolean readmePresent) {

  @Override
  public String toString() {
    return name;
  }
}
