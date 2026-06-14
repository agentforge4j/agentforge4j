package com.agentforge4j.core.workflow.requirement;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * Deserializes an arbitrary JSON value into its compact raw-JSON {@link String} form.
 *
 * <p>Lets a {@link WorkflowRequirement}'s {@code default} payload be carried opaquely: {@code core}
 * stores the raw JSON text without interpreting its shape (the {@code modelTier}-as-String convention). The configured
 * {@link RequirementResolver} or embedding application is responsible for parsing it.
 */
public final class RawJsonStringDeserializer extends JsonDeserializer<String> {

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    JsonNode node = parser.getCodec().readTree(parser);
    if (node == null || node.isNull()) {
      return null;
    }
    return node.toString();
  }
}
