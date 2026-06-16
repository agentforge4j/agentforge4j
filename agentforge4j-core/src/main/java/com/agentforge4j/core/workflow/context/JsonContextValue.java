// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON text stored as a context value; treated as opaque JSON until parsed by a consumer.
 *
 * @param json non-blank JSON document or fragment
 */
public record JsonContextValue(@JsonProperty(required = true) String json) implements ContextValue {

  public JsonContextValue {
    Validate.notBlank(json, "JsonContextValue json must not be blank");
  }
}
