// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.text;

/**
 * Strips markdown code-fence markers from LLM output text.
 */
public final class CodeFence {

  private CodeFence() {
  }

  /**
   * Removes markdown code fence markers from the input if present.
   * <p>
   * Strips leading {@code ```} followed by an optional language identifier, and trailing
   * {@code ```}. Returns the input unchanged if it does not start with backticks.
   *
   * @param input the potentially fence-marked string
   * @return the input with fences removed, or the input unchanged
   */
  public static String strip(String input) {
    if (input == null || input.isBlank() || !input.startsWith("```")) {
      return input;
    }

    int start = input.indexOf('\n');
    if (start < 0) {
      return input;
    }

    int end = input.lastIndexOf("```");
    return ((end > start)
        ? input.substring(start + 1, end)
        : input.substring(start + 1)).strip();
  }
}
