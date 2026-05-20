package com.agentforge4j.llm.api;

import com.agentforge4j.util.Validate;

/**
 * Byte offsets marking the end of each prompt layer in an assembled provider request body.
 * <p>
 * Layers are ordered most-stable-first: layer 1 is the most stable prefix, layer 2 follows layer 1,
 * and layer 3 follows layer 2. Each component is the cumulative byte offset at the
 * <strong>end</strong> of that layer in the assembled prompt (exclusive end index in the UTF-8
 * byte
 * sequence). A {@code null} component means that layer is not present in the assembled prompt.
 */
public record PromptLayerBoundaries(
    Integer layer1EndOffset,
    Integer layer2EndOffset,
    Integer layer3EndOffset) {

  public PromptLayerBoundaries {
    Validate.isTrue(layer1EndOffset == null || layer1EndOffset >= 0,
        "layer1EndOffset must be >= 0");
    Validate.isTrue(layer2EndOffset == null || layer2EndOffset >= 0,
        "layer2EndOffset must be >= 0");
    Validate.isTrue(layer3EndOffset == null || layer3EndOffset >= 0,
        "layer3EndOffset must be >= 0");

    Validate.isTrue(layer2EndOffset == null || layer1EndOffset != null,
        "layer2EndOffset requires layer1EndOffset");
    Validate.isTrue(layer3EndOffset == null || layer2EndOffset != null,
        "layer3EndOffset requires layer2EndOffset");

    Validate.isTrue(
        layer1EndOffset == null || layer2EndOffset == null || layer1EndOffset <= layer2EndOffset,
        "layer1EndOffset must be <= layer2EndOffset");
    Validate.isTrue(
        layer2EndOffset == null || layer3EndOffset == null || layer2EndOffset <= layer3EndOffset,
        "layer2EndOffset must be <= layer3EndOffset");
  }
}
