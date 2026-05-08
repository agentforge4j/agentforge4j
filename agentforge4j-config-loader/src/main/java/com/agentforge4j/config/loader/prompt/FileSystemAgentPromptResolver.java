package com.agentforge4j.config.loader.prompt;

import com.agentforge4j.util.Validate;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * Resolves agent prompt text from inline fields and bundle-local prompt files.
 */
@RequiredArgsConstructor
public final class FileSystemAgentPromptResolver implements AgentPromptResolver {

  private static final String SYSTEM_PROMPT_FILE_NAME = "systemprompt.md";
  private static final String BOUNDARIES_FILE_NAME = "boundaries.md";

  private final PromptLoader promptLoader;

  public String readSystemPrompt(Path agentDir) {
    Validate.notNull(agentDir, "Agent directory must not be null");
    return promptLoader.loadPrompt(agentDir, SYSTEM_PROMPT_FILE_NAME);
  }

  @Override
  public String readBoundariesPrompt(Path agentDir) {
    Validate.notNull(agentDir, "Agent directory must not be null");
    return promptLoader.loadPrompt(agentDir, BOUNDARIES_FILE_NAME);
  }
}
