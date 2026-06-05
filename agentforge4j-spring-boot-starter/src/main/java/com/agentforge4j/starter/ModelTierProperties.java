package com.agentforge4j.starter;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Spring Boot configuration under {@code agentforge4j.llm.model-tiers.<provider>.<tier>} that
 * overrides the shipped capability-tier to concrete-model mappings.
 *
 * <p>Example:
 * <pre>{@code
 * agentforge4j:
 *   llm:
 *     model-tiers:
 *       claude:
 *         powerful: claude-opus-4-8
 *       openai:
 *         lite: gpt-5.4-nano
 * }</pre>
 *
 * @param modelTiers provider name to (tier name to model string) overrides; {@code null} or empty
 *                   leaves the shipped defaults in place. Tier names are case-insensitive.
 */
@ConfigurationProperties(prefix = "agentforge4j.llm")
public record ModelTierProperties(
    Map<String, Map<String, String>> modelTiers
) {

}
