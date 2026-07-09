// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.contextpack;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.core.spi.contextpack.ContextPackLoader;
import com.agentforge4j.core.spi.contextpack.ContextPackVariant;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.util.Sha256;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads {@link ContextPack}s from a {@code context-packs} directory laid out as
 * {@code context-packs/<name>/pack.json} plus the variant content files named in that manifest.
 *
 * <p>Selection is non-recursive over the immediate subdirectories; a subdirectory without a
 * {@code pack.json} is a failure. Loading is all-or-nothing — a missing, malformed, or schema-invalid
 * manifest, a variant file that is missing or escapes the pack directory, or a duplicate pack name
 * fails the whole load with one exception naming every offending pack. Each variant's content is read
 * and a SHA-256 fingerprint is computed at load; packs are immutable per run.
 */
public final class FileSystemContextPackLoader implements ContextPackLoader {

  private static final System.Logger LOG =
      System.getLogger(FileSystemContextPackLoader.class.getName());

  private static final String MANIFEST_FILE = "pack.json";

  private final ObjectMapper objectMapper;
  private final Schema contextPackSchema;
  private final Path contextPacksRoot;

  /**
   * Creates a loader over a single {@code context-packs} directory.
   *
   * @param objectMapper     JSON mapper used to parse manifests; must not be {@code null}
   * @param schemaProvider   source of {@code context-pack.schema.json}; must not be {@code null}
   * @param contextPacksRoot directory holding the per-pack subdirectories; must be an existing
   *                         directory
   *
   * @throws IllegalArgumentException if {@code contextPacksRoot} is missing or not a directory
   */
  public FileSystemContextPackLoader(ObjectMapper objectMapper, SchemaProvider schemaProvider,
      Path contextPacksRoot) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    Validate.notNull(schemaProvider, "schemaProvider must not be null");
    this.contextPacksRoot = Validate.requireDirectory(contextPacksRoot,
        "Context packs directory does not exist: %s".formatted(contextPacksRoot));
    this.contextPackSchema = parseContextPackSchema(objectMapper, schemaProvider);
  }

  @Override
  public List<ContextPack> load() {
    List<Path> packDirs = listPackDirectories();
    LOG.log(System.Logger.Level.INFO, "Loading {0} context pack(s) from {1}", packDirs.size(),
        contextPacksRoot);
    List<String> errors = new ArrayList<>();
    Map<String, Path> dirByName = new LinkedHashMap<>();
    List<ContextPack> packs = new ArrayList<>();
    for (Path dir : packDirs) {
      ContextPack pack = loadPack(dir, errors);
      if (pack == null) {
        continue;
      }
      Path previous = dirByName.putIfAbsent(pack.name(), dir);
      if (previous != null) {
        errors.add("duplicate context pack name '%s' in %s and %s"
            .formatted(pack.name(), previous, dir));
        continue;
      }
      packs.add(pack);
    }
    Validate.isTrue(errors.isEmpty(), () -> new IllegalArgumentException(
        "Invalid context pack(s): %s".formatted(String.join("; ", errors))));
    return List.copyOf(packs);
  }

  private List<Path> listPackDirectories() {
    List<Path> dirs;
    try (Stream<Path> entries = Files.list(contextPacksRoot)) {
      dirs = entries.filter(Files::isDirectory).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read context packs directory: %s".formatted(contextPacksRoot), e);
    }
    for (Path dir : dirs) {
      String dirName = dir.getFileName().toString();
      Validate.requireWithinBase(contextPacksRoot, dirName,
          "context pack directory '%s' escapes the context packs root".formatted(dirName));
    }
    return dirs;
  }

  private ContextPack loadPack(Path dir, List<String> errors) {
    Path manifest = dir.resolve(MANIFEST_FILE);
    if (!Files.isRegularFile(manifest)) {
      errors.add("%s: missing %s".formatted(dir, MANIFEST_FILE));
      return null;
    }
    JsonNode node;
    try {
      node = objectMapper.readTree(Files.readString(manifest));
    } catch (JsonProcessingException e) {
      errors.add("%s: malformed JSON (%s)".formatted(manifest, e.getOriginalMessage()));
      return null;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read context pack manifest: %s".formatted(manifest),
          e);
    }
    List<Error> violations = contextPackSchema.validate(node);
    if (!violations.isEmpty()) {
      errors.add("%s: %s".formatted(manifest, violations.stream()
          .map(violation -> "%s: %s".formatted(violation.getInstanceLocation(),
              violation.getMessage()))
          .collect(Collectors.joining(", "))));
      return null;
    }
    Map<String, ContextPackVariant> variants = loadVariants(dir, node, errors);
    if (variants == null) {
      return null;
    }
    try {
      return new ContextPack(node.get("name").asText(), node.get("version").asText(),
          textOrNull(node, "description"), readTags(node), variants);
    } catch (IllegalArgumentException e) {
      errors.add("%s: %s".formatted(manifest, e.getMessage()));
      return null;
    }
  }

  private Map<String, ContextPackVariant> loadVariants(Path dir, JsonNode node, List<String> errors) {
    Map<String, ContextPackVariant> variants = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = node.get("variants").fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      String variantName = field.getKey();
      String fileName = field.getValue().asText();
      Path contentFile;
      try {
        contentFile = Validate.requireWithinBase(dir, fileName,
            "context pack variant '%s' file '%s' escapes the pack directory"
                .formatted(variantName, fileName));
      } catch (IllegalArgumentException e) {
        errors.add("%s: %s".formatted(dir, e.getMessage()));
        return null;
      }
      if (!Files.isRegularFile(contentFile)) {
        errors.add("%s: variant '%s' content file not found: %s"
            .formatted(dir, variantName, fileName));
        return null;
      }
      String content;
      try {
        content = readContent(contentFile);
      } catch (IOException e) {
        errors.add("%s: variant '%s' content file could not be read: %s (%s)"
            .formatted(dir, variantName, fileName, e.getMessage()));
        return null;
      }
      try {
        variants.put(variantName, new ContextPackVariant(variantName, content, Sha256.hex(content)));
      } catch (IllegalArgumentException e) {
        errors.add("%s: variant '%s' is invalid: %s".formatted(dir, variantName, e.getMessage()));
        return null;
      }
    }
    return variants;
  }

  private static String readContent(Path contentFile) throws IOException {
    return Files.readString(contentFile);
  }

  private static List<String> readTags(JsonNode node) {
    JsonNode tagsNode = node.get("tags");
    if (tagsNode == null || !tagsNode.isArray()) {
      return List.of();
    }
    List<String> tags = new ArrayList<>();
    tagsNode.forEach(tag -> tags.add(tag.asText()));
    return List.copyOf(tags);
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return (value == null || value.isNull()) ? null : value.asText();
  }

  private static Schema parseContextPackSchema(ObjectMapper objectMapper,
      SchemaProvider schemaProvider) {
    try {
      JsonNode schemaNode = objectMapper.readTree(schemaProvider.contextPackSchema());
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(schemaNode);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse context-pack.schema.json", e);
    }
  }
}
