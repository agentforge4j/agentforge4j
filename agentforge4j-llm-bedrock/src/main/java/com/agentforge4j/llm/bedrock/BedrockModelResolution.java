// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

/**
 * Resolution of a Bedrock {@code modelId} to its family, transport, and capabilities.
 *
 * @param family       the resolved model family
 * @param transport    the transport that serves the family
 * @param capabilities the family's capability descriptor
 */
record BedrockModelResolution(
    BedrockModelFamily family,
    BedrockTransportType transport,
    BedrockModelCapabilities capabilities) {

}
