package com.agentforge4j.llm.bedrock;

/**
 * Capability descriptor for a Bedrock model family.
 *
 * @param promptCache      whether the family's transport applies prompt caching for a request's
 *                         {@code promptLayerBoundaries}; when {@code false} the transport silently
 *                         ignores caching hints (consistent with non-caching providers)
 * @param maxContextTokens informational maximum context window for the family; metadata only, not
 *                         enforced (the request contract carries no input-token count to gate on)
 */
record BedrockModelCapabilities(boolean promptCache, int maxContextTokens) {

}
