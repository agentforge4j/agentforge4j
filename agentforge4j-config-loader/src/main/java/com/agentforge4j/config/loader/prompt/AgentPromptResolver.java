// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.prompt;

import java.nio.file.Path;

/**
 * Resolves effective system prompt text for an agent definition.
 */
public interface AgentPromptResolver {

  String readSystemPrompt(Path agentDir);

  String readBoundariesPrompt(Path agentDir);
}
