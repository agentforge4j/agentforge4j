package com.agentforge4j.config.loader.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;

class PromptLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadPrompt_readsUtf8File() throws IOException {
    Files.writeString(tempDir.resolve("note.md"), "hello 世界");
    PromptLoader loader = new PromptLoader();

    assertThat(loader.loadPrompt(tempDir, "note.md")).isEqualTo("hello 世界");
  }

  @Test
  void loadPrompt_rejectsPathOutsideBase() {
    PromptLoader loader = new PromptLoader();

    assertThatThrownBy(() -> loader.loadPrompt(tempDir, "../outside.md"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Prompt file path must not escape");
  }

  @Test
  void loadPrompt_rejectsBlankRelativePath() {
    PromptLoader loader = new PromptLoader();

    assertThatThrownBy(() -> loader.loadPrompt(tempDir, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void loadPrompt_rejectsNullBaseDir() {
    PromptLoader loader = new PromptLoader();

    assertThatThrownBy(() -> loader.loadPrompt(null, "x.md"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Base directory must not be null");
  }

  @Test
  void loadPrompt_returnsEmptyForMissingFile() {
    PromptLoader loader = new PromptLoader();

    assertThat(loader.loadPrompt(tempDir, "missing.md")).isEmpty();
  }

  @Test
  void loadPrompt_throwsWhenPathIsDirectory() throws IOException {
    Path promptDir = tempDir.resolve("prompts");
    Files.createDirectory(promptDir);
    PromptLoader loader = new PromptLoader();

    assertThatThrownBy(() -> loader.loadPrompt(tempDir, "prompts"))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("Failed to read prompt file");
  }
}
