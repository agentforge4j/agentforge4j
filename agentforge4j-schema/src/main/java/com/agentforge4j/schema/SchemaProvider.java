// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

/**
 * Provides JSON schema documents used to validate AgentForge4j definition files.
 */
public interface SchemaProvider {

  /**
   * Returns the workflow definition schema as UTF-8 text.
   *
   * @return workflow schema JSON content
   */
  String workflowSchema();

  /**
   * Returns the agent definition schema as UTF-8 text.
   *
   * @return agent schema JSON content
   */
  String agentSchema();

  /**
   * Returns the blueprint definition schema as UTF-8 text.
   *
   * @return blueprint schema JSON content
   */
  String blueprintSchema();

  /**
   * Returns the artifact definition schema as UTF-8 text.
   *
   * @return artifact schema JSON content
   */
  String artifactSchema();

  /**
   * Returns the integration definition schema as UTF-8 text.
   *
   * @return integration schema JSON content
   */
  String integrationSchema();

  /**
   * Returns the context-pack manifest schema as UTF-8 text.
   *
   * @return context-pack schema JSON content
   */
  String contextPackSchema();
}
