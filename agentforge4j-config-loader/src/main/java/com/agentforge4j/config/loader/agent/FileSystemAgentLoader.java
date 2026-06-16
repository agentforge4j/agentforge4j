// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.config.loader.prompt.AgentPromptResolver;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads agent bundles from filesystem directories.
 */
public final class FileSystemAgentLoader extends BaseAgentLoader {

  private static final System.Logger LOG = System.getLogger(FileSystemAgentLoader.class.getName());

  private final AgentPromptResolver agentPromptResolver;
  private final Path agentsRoot;

  public FileSystemAgentLoader(ObjectMapper objectMapper, AgentPromptResolver agentPromptResolver,
      Path agentsRoot) {
    super(objectMapper, "FileSystemAgentLoader");
    this.agentPromptResolver = Validate.notNull(agentPromptResolver,
        "AgentPromptResolver must not be null");
    this.agentsRoot = Validate.requireDirectory(agentsRoot,
        "Agents directory does not exist: %s".formatted(agentsRoot));
  }

  @Override
  protected List<String> listAgentDirectories() {
    try (Stream<Path> entries = Files.list(agentsRoot)) {
      return entries
          .filter(path -> Files.isDirectory(path) && isAgentBundleDir(path))
          .sorted()
          .map(path -> path.getFileName().toString())
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read agents directory: " + agentsRoot, e);
    }
  }

  private boolean isAgentBundleDir(Path path) {
    String name = path.getFileName().toString();
    return name.endsWith(AGENT_DIR_SUFFIX) && name.length() > AGENT_DIR_SUFFIX.length();
  }

  @Override
  protected void log(System.Logger.Level level, String message, Object... args) {
    LOG.log(level, message, args);
  }

  @Override
  protected AgentDefinitionFile readAgentFile(String entry) {
    Path agentBundleDir = requireAgentBundleDir(entry);
    Path jsonFile = Validate.requireWithinBase(agentBundleDir, AGENT_FILE_NAME,
        "Path escapes agent bundle directory: %s".formatted(AGENT_FILE_NAME));
    Validate.isTrue(Files.isRegularFile(jsonFile),
        "Agent bundle must contain %s: %s".formatted(AGENT_FILE_NAME, agentBundleDir));
    try {
      return objectMapper.readValue(jsonFile.toFile(), AgentDefinitionFile.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse agent file: %s".formatted(jsonFile), e);
    }
  }

  @Override
  protected String readSystemPromptFile(String entry) {
    return agentPromptResolver.readSystemPrompt(Validate.requireWithinBase(agentsRoot, entry,
        "Path escapes agents directory: %s".formatted(entry)));
  }

  @Override
  protected String readBoundariesFile(String entry) {
    return agentPromptResolver.readBoundariesPrompt(Validate.requireWithinBase(agentsRoot, entry,
        "Path escapes agents directory: %s".formatted(entry)));
  }

  private Path requireAgentBundleDir(String entry) {
    Validate.notBlank(entry, "Entry must not be blank");
    return Validate.requireWithinBase(agentsRoot, entry,
        "Path escapes agents directory: %s".formatted(entry));
  }
}
