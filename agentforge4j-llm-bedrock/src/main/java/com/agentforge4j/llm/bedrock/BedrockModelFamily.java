// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

/**
 * Bedrock model families recognised by this provider. Adding a family is a registry data change,
 * never a new module.
 */
enum BedrockModelFamily {
  ANTHROPIC,
  LLAMA,
  NOVA,
  TITAN
}
