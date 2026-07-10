// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import com.agentforge4j.core.workflow.LedgerDefinition;
import com.agentforge4j.core.workflow.LedgerMergeStrategy;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Resolves and validates a {@link LedgerDefinition#schemaRef()} against a classpath resource under
 * {@code schema/}, and — for {@link LedgerMergeStrategy#MERGE_BY_KEY} — confirms
 * {@link LedgerDefinition#mergeKeyField()} names a field the resolved schema actually permits on a
 * ledger entry.
 *
 * <p>{@code schemaRef} resolution is deliberately a config-loader-owned concern, separate from
 * {@code agentforge4j.schema.SchemaProvider} (owner decision, 2026-07-10): {@code SchemaProvider}'s
 * fixed accessors are the shipped definition-file schemas (workflow/agent/blueprint/artifact/
 * integration/context-pack); a ledger's schema reference is a distinct, user-declared responsibility
 * this class owns instead. {@code SchemaProvider}'s interface is untouched by this class.
 *
 * <p><strong>JPMS note.</strong> The shipped ledger schemas live in {@code agentforge4j.schema}'s
 * {@code schema/ledger/} resources. Resolving them from this module needed
 * {@code agentforge4j.schema}'s module descriptor to add {@code opens schema.ledger;} (alongside its
 * existing {@code opens schema;}) — {@code Class::getResourceAsStream} only ever resolves within the
 * calling class's own module regardless of a {@code requires} dependency, and JPMS resource
 * encapsulation for non-class resources follows the same directory-as-package convention as Java
 * source packages, so {@code opens schema;} alone does not cover its {@code ledger} subdirectory.
 * Confirmed empirically (a failing resolution test before this was added), not assumed.
 *
 * <p><strong>Scope, as built — classpath resolution only.</strong> {@link LedgerDefinition#schemaRef()}'s
 * Javadoc describes a "classpath- or bundle-relative id," but bundle-relative resolution needs the
 * loading workflow's bundle root directory, which is not available at the point
 * {@code WorkflowValidator} runs: {@code WorkflowDirectoryLoad} merges every loaded workflow into a
 * flat {@code Map<String, WorkflowDefinition>} with no per-workflow bundle path carried forward, and
 * shipped/filesystem/classpath-loaded bundles are indistinguishable by the time validation sees them.
 * Adding that would mean threading a bundle-root path alongside every {@code WorkflowDefinition}
 * through the whole load pipeline — a separate, larger change, not implied by this class. Only
 * classpath-relative {@code schemaRef} values resolve today; a bundle-relative one fails as an
 * unresolved classpath resource, with a message naming exactly that.
 *
 * <p><strong>{@code mergeKeyField} check, as built.</strong> networknt's own lazy {@code $ref}
 * resolution (used by {@link Schema#validate}) could not be made to resolve the shipped ledger
 * schemas' relative {@code $ref} into their shared {@code ledger-envelope.schema.json} reliably in
 * this environment (a {@code FileNotFoundException} from networknt's own URI-based fetch, for a file
 * verified to exist on disk — a networknt/classpath-URI interop issue, not a JPMS one, since
 * existence resolution via {@link SchemaRegistry#getSchema} already succeeds by that point). Rather
 * than depend on that, this class dereferences a single top-level {@code $ref} itself — reading the
 * referenced sibling classpath resource (relative to {@code schemaRef}'s own directory) and
 * following its JSON-pointer fragment — then inspects {@code properties.entries.items} on the
 * resulting node directly: {@code mergeKeyField} passes when it is an explicitly declared property,
 * or when {@code additionalProperties} is not explicitly {@code false} (the shipped schemas declare
 * {@code additionalProperties: true} — domain fields are deliberately open). This assumes the shipped
 * envelope convention (single-hop {@code $ref}, {@code entries} as a top-level array-of-objects
 * property) — a schema shaped differently is not fully understood, but only what {@code entries.items}
 * looks like is asserted, so a plausible custom schema of the same shape works correctly too.
 */
final class LedgerSchemaResolver {

  private static final String CLASSPATH_ROOT = "schema/";

  private final ObjectMapper objectMapper;
  private final Function<String, InputStream> resourceLoader;

  LedgerSchemaResolver(ObjectMapper objectMapper) {
    // ClassLoader-based lookup (not Class::getResourceAsStream), deliberately: this class lives in
    // agentforge4j.config.loader, but the shipped ledger schemas live in agentforge4j.schema's
    // resources — Class::getResourceAsStream resolves only within the calling class's own module, so
    // it cannot see another module's resources even with a `requires` dependency (confirmed by a
    // failing empirical test before this fix). ClassLoader::getResourceAsStream searches the whole
    // module/classpath's resource index, which is what classpath-relative resolution across module
    // boundaries actually needs — together with agentforge4j.schema's module-info now opening
    // schema.ledger (see class Javadoc).
    this(objectMapper, name -> LedgerSchemaResolver.class.getClassLoader().getResourceAsStream(name));
  }

  LedgerSchemaResolver(ObjectMapper objectMapper, Function<String, InputStream> resourceLoader) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.resourceLoader = Validate.notNull(resourceLoader, "resourceLoader must not be null");
  }

  /**
   * Resolves and validates {@code ledger}'s {@code schemaRef}, and its {@code mergeKeyField} when
   * {@code mergeStrategy} is {@code MERGE_BY_KEY}.
   *
   * @param ledger the ledger declaration to validate; must not be {@code null}
   *
   * @throws IllegalArgumentException when {@code schemaRef} does not resolve to a classpath
   *                                  resource, the resource is not a valid JSON schema, or (for
   *                                  {@code MERGE_BY_KEY}) {@code mergeKeyField} is not a field the
   *                                  schema permits on a ledger entry
   */
  void validate(LedgerDefinition ledger) {
    Validate.notNull(ledger, "ledger must not be null");
    rejectPathTraversal(ledger, ledger.schemaRef());
    String resourcePath = CLASSPATH_ROOT + ledger.schemaRef();
    JsonNode schemaNode = readSchemaNode(ledger, resourcePath);
    confirmValidJsonSchema(ledger, schemaNode);
    if (ledger.mergeStrategy() == LedgerMergeStrategy.MERGE_BY_KEY) {
      JsonNode resolved = dereferenceTopLevelRef(ledger, resourcePath, schemaNode);
      validateMergeKeyField(ledger, resolved);
    }
  }

  /**
   * Rejects a {@code schemaRef}/{@code $ref} sibling-file value containing a {@code ..} path
   * segment, mirroring {@code FileSystemContextPackLoader}'s traversal guard: both build a classpath
   * resource path by concatenating an author-controlled string onto a fixed root, so both need the
   * same containment check.
   */
  private static void rejectPathTraversal(LedgerDefinition ledger, String refValue) {
    Validate.isTrue(!refValue.contains(".."), () -> new IllegalArgumentException(
        "Ledger '%s' schemaRef '%s' must not contain '..' path segments"
            .formatted(ledger.id(), refValue)));
  }

  private JsonNode readSchemaNode(LedgerDefinition ledger, String resourcePath) {
    try (InputStream stream = resourceLoader.apply(resourcePath)) {
      Validate.isTrue(stream != null, () -> new IllegalArgumentException(
          "Ledger '%s' declares schemaRef '%s', which does not resolve to classpath resource '%s'"
              .formatted(ledger.id(), ledger.schemaRef(), resourcePath)));
      return objectMapper.readTree(stream);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Ledger '%s' schemaRef '%s' could not be read: %s"
              .formatted(ledger.id(), ledger.schemaRef(), e.getMessage()), e);
    }
  }

  private static void confirmValidJsonSchema(LedgerDefinition ledger, JsonNode schemaNode) {
    try {
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Ledger '%s' schemaRef '%s' is not a valid JSON schema: %s"
              .formatted(ledger.id(), ledger.schemaRef(), e.getMessage()), e);
    }
  }

  /**
   * Dereferences a single top-level {@code {"$ref": "<file>#<pointer>"}} indirection (the shape every
   * shipped ledger schema uses), reading {@code <file>} relative to {@code resourcePath}'s own
   * directory and following {@code <pointer>} as a JSON pointer. A schema without a top-level
   * {@code $ref} (or one that already declares {@code properties} itself) is returned unchanged.
   */
  private JsonNode dereferenceTopLevelRef(LedgerDefinition ledger, String resourcePath,
      JsonNode schemaNode) {
    JsonNode refNode = schemaNode.get("$ref");
    if (refNode == null || !refNode.isTextual() || schemaNode.has("properties")) {
      return schemaNode;
    }
    String ref = refNode.asText();
    int hashIndex = ref.indexOf('#');
    String refFile = hashIndex < 0 ? ref : ref.substring(0, hashIndex);
    String refPointer = hashIndex < 0 ? "" : ref.substring(hashIndex + 1);
    if (refFile.isBlank()) {
      return atOrThrow(ledger, schemaNode, refPointer, "the schema itself");
    }
    Validate.isTrue(!refFile.contains("://"), () -> new IllegalArgumentException(
        "Ledger '%s' schemaRef '%s' has a $ref '%s' to an absolute/external URL, which this "
            + "single-hop classpath resolver does not support".formatted(ledger.id(),
            ledger.schemaRef(), refFile)));
    rejectPathTraversal(ledger, refFile);
    int lastSlash = resourcePath.lastIndexOf('/');
    String baseDir = lastSlash < 0 ? "" : resourcePath.substring(0, lastSlash + 1);
    String siblingPath = baseDir + refFile;
    try (InputStream stream = resourceLoader.apply(siblingPath)) {
      Validate.isTrue(stream != null, () -> new IllegalArgumentException(
          ("Ledger '%s' schemaRef '%s' references '%s' via $ref, which does not resolve to "
              + "classpath resource '%s'").formatted(ledger.id(), ledger.schemaRef(), refFile,
              siblingPath)));
      return atOrThrow(ledger, objectMapper.readTree(stream), refPointer, "'%s'".formatted(
          siblingPath));
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Ledger '%s' schemaRef '%s' $ref target '%s' could not be read: %s"
              .formatted(ledger.id(), ledger.schemaRef(), siblingPath, e.getMessage()), e);
    }
  }

  /**
   * Resolves {@code pointer} within {@code root}, or returns {@code root} unchanged when
   * {@code pointer} is blank (no fragment). A non-blank pointer that does not resolve fails loud —
   * silently falling back to {@code root} would mask a typo'd fragment as "nothing to check," letting
   * an unrelated {@code mergeKeyField} pass validation it should have failed.
   */
  private static JsonNode atOrThrow(LedgerDefinition ledger, JsonNode root, String pointer,
      String refDescription) {
    if (pointer.isBlank()) {
      return root;
    }
    String jsonPointer = pointer.startsWith("/") ? pointer : "/" + pointer;
    JsonNode result = root.at(jsonPointer);
    Validate.isTrue(!result.isMissingNode(), () -> new IllegalArgumentException(
        "Ledger '%s' schemaRef '%s' has a $ref fragment '#%s' that does not resolve within %s"
            .formatted(ledger.id(), ledger.schemaRef(), pointer, refDescription)));
    return result;
  }

  private static void validateMergeKeyField(LedgerDefinition ledger, JsonNode resolvedSchema) {
    JsonNode items = resolvedSchema.at("/properties/entries/items");
    // A schema shape this single-hop resolver doesn't understand (see class Javadoc: at most one
    // $ref hop, entries as a top-level array-of-objects property) fails loud rather than silently
    // skipping the check — the alternative would let any mergeKeyField pass unvalidated whenever the
    // schema shape is even slightly unfamiliar, defeating the point of this validation.
    Validate.isTrue(!items.isMissingNode(), () -> new IllegalArgumentException(
        ("Ledger '%s' schemaRef '%s' does not resolve to a schema shape mergeKeyField can be "
            + "validated against (expected 'properties.entries.items' reachable within at most one "
            + "$ref hop)").formatted(ledger.id(), ledger.schemaRef())));
    JsonNode properties = items.get("properties");
    if (properties != null && properties.has(ledger.mergeKeyField())) {
      return;
    }
    JsonNode additionalProperties = items.get("additionalProperties");
    boolean open = additionalProperties == null || !additionalProperties.isBoolean()
        || additionalProperties.asBoolean();
    Validate.isTrue(open, () -> new IllegalArgumentException(
        ("Ledger '%s' declares mergeKeyField '%s', which is not a property schema '%s' declares on "
            + "a ledger entry, and the schema's entry shape disallows additional properties")
            .formatted(ledger.id(), ledger.mergeKeyField(), ledger.schemaRef())));
  }
}
