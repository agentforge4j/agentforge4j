// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Instruct the runtime to create a file with the given content at the given path.
 */
public record CreateFileCommand(
    @JsonProperty(value = "path", required = true)
    @JsonAlias("filePath")
    String path,
    @JsonProperty(required = true)
    String content
) implements LlmCommand {

  public CreateFileCommand {
    Validate.notBlank(path, "CreateFileCommand path must not be blank");
    Validate.notNull(content,
        "CreateFileCommand content must not be null for path: %s".formatted(path));
  }
}
