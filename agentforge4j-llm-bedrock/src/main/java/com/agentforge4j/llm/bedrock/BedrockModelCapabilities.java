// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

/**
 * Capability descriptor for a Bedrock model family.
 *
 * @param promptCache whether the family's transport applies prompt caching for a request's
 *                    {@code promptLayerBoundaries}; when {@code false} the transport silently
 *                    ignores caching hints (consistent with non-caching providers)
 */
record BedrockModelCapabilities(boolean promptCache) {

}
