package com.agentforge4j.tools.http;

/**
 * How the request body of an {@link HttpEndpointDefinition} is formed from leftover arguments.
 */
public enum BodyMode {

  /**
   * No request body; every argument routes to a path placeholder or the query string.
   */
  NONE,

  /**
   * A JSON object body built from the arguments not consumed by path/query placeholders.
   */
  JSON
}
