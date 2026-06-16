// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.ContextValue;
import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ContextValueExtractor<T extends ContextValue> {

  JsonNode extract(T value);
}
