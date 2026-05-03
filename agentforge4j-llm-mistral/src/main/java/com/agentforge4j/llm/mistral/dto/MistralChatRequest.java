package com.agentforge4j.llm.mistral.dto;

import java.util.List;

/**
 * Request body for Mistral {@code /v1/chat/completions}.
 */
public record MistralChatRequest(String model, List<MistralMessage> messages) {

}
