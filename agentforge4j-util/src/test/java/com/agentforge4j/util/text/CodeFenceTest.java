// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeFenceTest {

  @Test
  void should_strip_markdown_code_fence() {
    String input = "```json\n{\"key\": \"value\"}\n```";

    String result = CodeFence.strip(input);

    assertEquals("{\"key\": \"value\"}", result);
  }

  @Test
  void should_strip_code_fence_with_language_spec() {
    String input = "```python\nprint('hello')\n```";

    String result = CodeFence.strip(input);

    assertEquals("print('hello')", result);
  }

  @Test
  void should_return_input_unchanged_when_no_fence() {
    String input = "{\"key\": \"value\"}";

    String result = CodeFence.strip(input);

    assertEquals(input, result);
  }

  @Test
  void should_return_input_unchanged_when_null() {
    String result = CodeFence.strip(null);

    assertNull(result);
  }

  @Test
  void should_handle_fence_without_closing_marker() {
    String input = "```json\n{\"key\": \"value\"}";

    String result = CodeFence.strip(input);

    assertEquals("{\"key\": \"value\"}", result);
  }

  @Test
  void should_handle_fence_without_newline_after_opening() {
    String input = "```\n```";

    String result = CodeFence.strip(input);

    assertEquals("", result);
  }

  @Test
  void should_strip_leading_trailing_whitespace_from_content() {
    String input = "```\n  content with spaces  \n```";

    String result = CodeFence.strip(input);

    assertEquals("content with spaces", result);
  }

  @Test
  void should_handle_multiline_content_with_code_fence() {
    String input = "```json\nline1\nline2\nline3\n```";

    String result = CodeFence.strip(input);

    assertTrue(result.contains("line1"));
    assertTrue(result.contains("line2"));
    assertTrue(result.contains("line3"));
  }

  @Test
  void should_only_strip_outermost_fence() {
    String input = "```\nouter ``` inner\n```";

    String result = CodeFence.strip(input);

    assertTrue(result.contains("outer"));
    assertTrue(result.contains("inner"));
  }

  @Test
  void should_return_empty_string_unchanged_when_not_a_fence() {
    assertEquals("", CodeFence.strip(""));
  }

  @Test
  void should_return_opening_fence_unchanged_when_no_newline_follows_opening_ticks() {
    assertEquals("```json", CodeFence.strip("```json"));
  }
}
