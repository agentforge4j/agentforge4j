// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerSchemaResolverTest {

  private final LedgerSchemaResolver resolver = new LedgerSchemaResolver(new ObjectMapper());

  private static LedgerSchemaResolver resolverOver(Map<String, String> resourcesByPath) {
    return new LedgerSchemaResolver(new ObjectMapper(), path -> {
      String content = resourcesByPath.get(path);
      return content == null ? null
          : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    });
  }

  @Test
  void resolvesAShippedLedgerSchemaAndAcceptsAppendMergeStrategy() {
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "ledger/requirement-ledger.schema.json", LedgerMergeStrategy.APPEND, null);

    assertThatCode(() -> resolver.validate(ledger)).doesNotThrowAnyException();
  }

  @Test
  void rejectsAnUnresolvableSchemaRef() {
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/does-not-exist.schema.json",
        LedgerMergeStrategy.APPEND, null);

    assertThatThrownBy(() -> resolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not resolve to classpath resource");
  }

  @Test
  void acceptsMergeByKeyOnTheIdField() {
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "ledger/requirement-ledger.schema.json", LedgerMergeStrategy.MERGE_BY_KEY, "id");

    assertThatCode(() -> resolver.validate(ledger)).doesNotThrowAnyException();
  }

  @Test
  void acceptsMergeByKeyOnAnArbitraryFieldSinceTheShippedSchemaIsOpen() {
    // The shipped ledger-envelope schema declares only 'id' with additionalProperties:true on each
    // entry — domain-specific fields are deliberately open, so any mergeKeyField is schema-permitted.
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "ledger/requirement-ledger.schema.json", LedgerMergeStrategy.MERGE_BY_KEY, "priority");

    assertThatCode(() -> resolver.validate(ledger)).doesNotThrowAnyException();
  }

  @Test
  void rejectsASchemaRefThatIsNotValidJson() {
    LedgerSchemaResolver customResolver = resolverOver(
        Map.of("schema/ledger/broken.schema.json", "not json"));
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/broken.schema.json",
        LedgerMergeStrategy.APPEND, null);

    assertThatThrownBy(() -> customResolver.validate(ledger))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void rejectsMergeKeyFieldNotDeclaredOnAClosedCustomSchema() {
    String closedSchema = """
        {"type":"object","required":["entries"],"properties":{"entries":{"type":"array",
         "items":{"type":"object","required":["id"],"properties":{"id":{"type":"string"}},
         "additionalProperties":false}}}}""";
    LedgerSchemaResolver customResolver = resolverOver(
        Map.of("schema/ledger/closed.schema.json", closedSchema));
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/closed.schema.json",
        LedgerMergeStrategy.MERGE_BY_KEY, "priority");

    assertThatThrownBy(() -> customResolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mergeKeyField 'priority'")
        .hasMessageContaining("disallows additional properties");
  }

  @Test
  void acceptsMergeKeyFieldExplicitlyDeclaredOnAClosedCustomSchema() {
    String closedSchema = """
        {"type":"object","required":["entries"],"properties":{"entries":{"type":"array",
         "items":{"type":"object","required":["id"],"properties":{"id":{"type":"string"},
         "priority":{"type":"string"}},"additionalProperties":false}}}}""";
    LedgerSchemaResolver customResolver = resolverOver(
        Map.of("schema/ledger/closed-with-priority.schema.json", closedSchema));
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "ledger/closed-with-priority.schema.json", LedgerMergeStrategy.MERGE_BY_KEY, "priority");

    assertThatCode(() -> customResolver.validate(ledger)).doesNotThrowAnyException();
  }
}
