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

  @Test
  void rejectsATypoedRefFragmentInsteadOfSilentlyPassingMergeKeyField() {
    String envelope = """
        {"$defs":{"Envelope":{"type":"object","properties":{"entries":{"type":"array",
         "items":{"type":"object","properties":{"id":{"type":"string"}},
         "additionalProperties":false}}}}}}""";
    String wrapper = """
        {"$ref": "envelope.schema.json#/$defs/Typo"}""";
    LedgerSchemaResolver customResolver = resolverOver(Map.of(
        "schema/ledger/wrapper.schema.json", wrapper,
        "schema/ledger/envelope.schema.json", envelope));
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/wrapper.schema.json",
        LedgerMergeStrategy.MERGE_BY_KEY, "bogus");

    assertThatThrownBy(() -> customResolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not resolve within");
  }

  @Test
  void rejectsAMultiHopRefChainInsteadOfSilentlyPassingMergeKeyField() {
    // No fragment on the top-level $ref: dereferenceTopLevelRef's single hop lands on
    // second.schema.json's raw content unchanged, which is itself just another $ref indirection
    // (no properties.entries.items reachable within the one hop this resolver supports).
    String firstHop = """
        {"$ref": "second.schema.json"}""";
    String secondHop = """
        {"$ref": "third.schema.json#/$defs/Envelope"}""";
    String thirdHop = """
        {"$defs":{"Envelope":{"type":"object","properties":{"entries":{"type":"array",
         "items":{"type":"object","properties":{"id":{"type":"string"}},
         "additionalProperties":false}}}}}}""";
    LedgerSchemaResolver customResolver = resolverOver(Map.of(
        "schema/ledger/first.schema.json", firstHop,
        "schema/ledger/second.schema.json", secondHop,
        "schema/ledger/third.schema.json", thirdHop));
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/first.schema.json",
        LedgerMergeStrategy.MERGE_BY_KEY, "bogus");

    assertThatThrownBy(() -> customResolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not resolve to a schema shape mergeKeyField can be validated");
  }

  @Test
  void rejectsPathTraversalInSchemaRef() {
    LedgerDefinition ledger = new LedgerDefinition("requirements",
        "../../../etc/passwd", LedgerMergeStrategy.APPEND, null);

    assertThatThrownBy(() -> resolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain '..'");
  }

  @Test
  void rejectsPathTraversalInASiblingRefFile() {
    String traversalRef = """
        {"$ref": "../../secrets.schema.json#/$defs/Envelope"}""";
    LedgerSchemaResolver customResolver = resolverOver(
        Map.of("schema/ledger/traversal.schema.json", traversalRef));
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/traversal.schema.json",
        LedgerMergeStrategy.MERGE_BY_KEY, "id");

    assertThatThrownBy(() -> customResolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain '..'");
  }

  @Test
  void rejectsAnAbsoluteExternalRefWithADiagnosticMessage() {
    String externalRef = """
        {"$ref": "https://example.com/schema.json#/$defs/Envelope"}""";
    LedgerSchemaResolver customResolver = resolverOver(
        Map.of("schema/ledger/external.schema.json", externalRef));
    LedgerDefinition ledger = new LedgerDefinition("requirements", "ledger/external.schema.json",
        LedgerMergeStrategy.MERGE_BY_KEY, "id");

    assertThatThrownBy(() -> customResolver.validate(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("absolute/external URL");
  }
}
