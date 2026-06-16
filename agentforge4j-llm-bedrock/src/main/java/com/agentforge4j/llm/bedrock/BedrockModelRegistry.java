// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * Resolves a Bedrock {@code modelId} to its {@link BedrockModelFamily},
 * {@link BedrockTransportType}, and {@link BedrockModelCapabilities}, by matching the vendor prefix
 * after stripping any cross-region inference-profile prefix.
 * <p>
 * The region prefix is removed for family <em>detection</em> only; the original {@code modelId} is
 * always sent to Bedrock unchanged.
 */
final class BedrockModelRegistry {

  /**
   * Cross-region inference-profile prefixes stripped for detection. Maintenance point: a future
   * region prefix not listed here causes such model ids to resolve as unknown.
   */
  private static final Pattern REGION_PREFIX =
      Pattern.compile("^(us|eu|apac|us-gov)\\.", Pattern.CASE_INSENSITIVE);

  private record Entry(
      String prefix,
      BedrockModelFamily family,
      BedrockTransportType transport,
      BedrockModelCapabilities capabilities) {

  }

  private static final List<Entry> ENTRIES = List.of(
      new Entry("anthropic.", BedrockModelFamily.ANTHROPIC, BedrockTransportType.INVOKE_MODEL,
          new BedrockModelCapabilities(true)),
      new Entry("meta.llama", BedrockModelFamily.LLAMA, BedrockTransportType.CONVERSE,
          new BedrockModelCapabilities(false)),
      new Entry("amazon.nova", BedrockModelFamily.NOVA, BedrockTransportType.CONVERSE,
          new BedrockModelCapabilities(false)),
      new Entry("amazon.titan", BedrockModelFamily.TITAN, BedrockTransportType.CONVERSE,
          new BedrockModelCapabilities(false)));

  /**
   * Resolves {@code modelId} to its family, transport, and capabilities.
   *
   * @param modelId the Bedrock model id (optionally region-prefixed)
   *
   * @return the resolution
   *
   * @throws LlmInvocationException if the id is blank or no registered family matches it
   */
  BedrockModelResolution resolve(String modelId) {
    Validate.notBlank(modelId,
        () -> new LlmInvocationException("Bedrock modelId must not be blank"));
    Entry entry = Validate.notNull(match(modelId), () -> new LlmInvocationException(
        "Unsupported Bedrock modelId (no registered family): %s".formatted(modelId)));
    return new BedrockModelResolution(entry.family(), entry.transport(), entry.capabilities());
  }

  /**
   * Returns whether {@code modelId} resolves to a known family, without throwing. Used for
   * config-time validation.
   *
   * @param modelId the Bedrock model id
   *
   * @return {@code true} if a registered family matches
   */
  boolean supports(String modelId) {
    return match(modelId) != null;
  }

  private static Entry match(String modelId) {
    if (StringUtils.isBlank(modelId)) {
      return null;
    }
    String detection = REGION_PREFIX.matcher(modelId.strip()).replaceFirst("");
    for (Entry entry : ENTRIES) {
      if (Strings.CI.startsWith(detection, entry.prefix())) {
        return entry;
      }
    }
    return null;
  }
}
