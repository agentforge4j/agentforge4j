// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.tools.http;

/**
 * HTTP methods an {@link HttpEndpointDefinition} may use.
 */
public enum HttpMethod {

  /**
   * HTTP {@code GET}; idempotent, no request body.
   */
  GET,

  /**
   * HTTP {@code POST}; non-idempotent, may carry a JSON body.
   */
  POST,

  /**
   * HTTP {@code PUT}; idempotent, may carry a JSON body.
   */
  PUT,

  /**
   * HTTP {@code PATCH}; non-idempotent, may carry a JSON body.
   */
  PATCH,

  /**
   * HTTP {@code DELETE}; idempotent, no request body.
   */
  DELETE,

  /**
   * HTTP {@code HEAD}; idempotent, no request body.
   */
  HEAD
}
