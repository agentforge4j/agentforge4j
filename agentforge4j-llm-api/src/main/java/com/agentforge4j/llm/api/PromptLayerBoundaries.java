package com.agentforge4j.llm.api;

/**
 * Byte offsets marking the end of each prompt layer in an assembled provider request body.
 * <p>
 * Layers are ordered most-stable-first: layer 1 is the most stable prefix, layer 2 follows layer 1,
 * and layer 3 follows layer 2. Each component is the cumulative byte offset at the
 * <strong>end</strong> of that layer in the assembled prompt (exclusive end index in the UTF-8 byte
 * sequence). A {@code null} component means that layer is not present in the assembled prompt.
 */
public record PromptLayerBoundaries(
    Integer layer1EndOffset,
    Integer layer2EndOffset,
    Integer layer3EndOffset) {

}
