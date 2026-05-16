package com.agentforge4j.config.loader.prompt;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads UTF-8 prompt text files from a base directory.
 */
public final class PromptLoader {

  /**
   * Reads a prompt file resolved relative to a base directory.
   *
   * @param baseDir  base directory used for safe resolution
   * @param filePath relative path to the prompt file
   * @return prompt file contents as UTF-8 text, or an empty string when the file is absent
   * @throws IllegalArgumentException when inputs are blank or escape the base directory
   * @throws UncheckedIOException     when the file exists but cannot be read
   */
  public String loadPrompt(Path baseDir, String filePath) {
    Validate.notNull(baseDir, "Base directory must not be null");
    Validate.notBlank(filePath, "Prompt filePath must not be blank");
    Path promptPath = Validate.requireWithinBase(baseDir, filePath,
        "Prompt file path must not escape base directory");
    if (Files.notExists(promptPath)) {
      return "";
    }
    try {
      return Files.readString(promptPath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read prompt file: %s".formatted(promptPath), e);
    }
  }
}
