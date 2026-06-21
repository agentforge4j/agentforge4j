// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads agent definitions from shipped classpath bundles.
 */
public final class ClasspathAgentLoader extends BaseAgentLoader {

  private static final System.Logger LOG = System.getLogger(ClasspathAgentLoader.class.getName());
  public static final String SHIPPED_AGENTS_ROOT = "/shipped-agents";
  private final String rootPath;

  public ClasspathAgentLoader(ObjectMapper objectMapper, String rootPath) {
    super(objectMapper, "ClasspathAgentLoader");
    this.rootPath = determineRootPath(Validate.notBlank(rootPath, "rootPath must not be blank"));
  }

  @Override
  protected void log(Level level, String message, Object... args) {
    LOG.log(level, message, args);
  }

  @Override
  protected List<String> listAgentDirectories() {
    return List.copyOf(AgentBundleLocator.shippedAgentIds());
  }

  private String agentBundleDirectoryPrefix(String entry) {
    validateBundleEntryId(entry);
    String dirName = entry.endsWith(AGENT_DIR_SUFFIX) ? entry : entry + AGENT_DIR_SUFFIX;
    return rootPath + dirName + "/";
  }

  private static void validateBundleEntryId(String entry) {
    Validate.notBlank(entry, "Agent bundle entry id must not be blank");
    Validate.isTrue(!entry.contains(".."),
        "Agent bundle entry id must not contain path traversal: %s".formatted(entry));
    Validate.isTrue(!entry.contains("/") && !entry.contains("\\"),
        "Agent bundle entry id must not contain path separators: %s".formatted(entry));
  }

  @Override
  protected AgentDefinitionFile readAgentFile(String entry) {
    validateBundleEntryId(entry);
    URL jsonUrl = classpathUrlForAgentJson(agentBundleDirectoryPrefix(entry) + AGENT_FILE_NAME);
    try (InputStream stream = jsonUrl.openStream()) {
      return objectMapper.readValue(stream, AgentDefinitionFile.class);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read agent definition for entry '%s' at root path '%s'"
              .formatted(entry, rootPath), e);
    }
  }

  @Override
  protected String readSystemPromptFile(String entry) {
    return readFile(entry, SYSTEM_PROMPT_FILE_NAME);
  }

  @Override
  protected String readBoundariesFile(String entry) {
    return readFile(entry, BOUNDARIES_FILE_NAME);
  }

  private String readFile(String entry, String fileName) {
    validateBundleEntryId(entry);
    URL jsonUrl = classpathUrlForAgentJson(agentBundleDirectoryPrefix(entry) + fileName);
    if (jsonUrl != null) {
      try (InputStream stream = jsonUrl.openStream()) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        // Do not throw anything if the file is not found
      }
    }
    return "";
  }

  private String determineRootPath(String path) {
    return path + (path.endsWith("/") ? "" : "/");
  }

  // Single classloader lookup: the agent locator, the workflow locator and this loader now share
  // one module, and the shipped catalog (when present) ships as a separate jar on the same loader,
  // so one ClassLoader.getResource over the (non-encapsulated, hyphenated) shipped roots resolves
  // both in-module test bundles and an external catalog. ClassLoader paths carry no leading slash.
  private URL classpathUrlForAgentJson(String classpathPath) {
    String resourcePath = classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
    return ClasspathAgentLoader.class.getClassLoader().getResource(resourcePath);
  }
}
