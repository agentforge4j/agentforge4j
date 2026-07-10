// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.contextpack;

import com.agentforge4j.core.spi.contextpack.ContextPack;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.agentforge4j.schema.SchemaProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemContextPackLoaderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final SchemaProvider schemaProvider = new ClasspathSchemaProvider();

  @TempDir
  Path root;

  private FileSystemContextPackLoader loader() {
    return new FileSystemContextPackLoader(mapper, schemaProvider, root);
  }

  private Path pack(String name) throws IOException {
    return Files.createDirectories(root.resolve(name));
  }

  private static void write(Path dir, String file, String content) throws IOException {
    Files.writeString(dir.resolve(file), content);
  }

  @Test
  void loadsValidPackWithVariantsAndFingerprints() throws Exception {
    Path dir = pack("coding-standards");
    write(dir, "pack.json", """
        {"name":"coding-standards","version":"1.2.0","description":"...","tags":["software-delivery"],
         "variants":{"full":"content.md","compact":"content.compact.md"}}""");
    write(dir, "content.md", "FULL CONTENT");
    write(dir, "content.compact.md", "COMPACT");

    List<ContextPack> packs = loader().load();

    assertThat(packs).hasSize(1);
    ContextPack pack = packs.get(0);
    assertThat(pack.name()).isEqualTo("coding-standards");
    assertThat(pack.version()).isEqualTo("1.2.0");
    assertThat(pack.tags()).containsExactly("software-delivery");
    assertThat(pack.variants()).containsOnlyKeys("full", "compact");
    assertThat(pack.variants().get("full").content()).isEqualTo("FULL CONTENT");
    assertThat(pack.variants().get("full").fingerprint())
        .hasSize(64).matches("[0-9a-f]+");
    assertThat(pack.variants().get("compact").fingerprint())
        .isNotEqualTo(pack.variants().get("full").fingerprint());
  }

  @Test
  void emptyRootLoadsNoPacks() {
    assertThat(loader().load()).isEmpty();
  }

  @Test
  void rejectsSubdirectoryWithoutManifest() throws Exception {
    pack("no-manifest");
    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing pack.json");
  }

  @Test
  void rejectsMalformedManifest() throws Exception {
    Path dir = pack("broken");
    write(dir, "pack.json", "{not valid json");
    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("malformed JSON");
  }

  @Test
  void rejectsSchemaInvalidManifest() throws Exception {
    Path dir = pack("no-variants");
    write(dir, "pack.json", """
        {"name":"no-variants","version":"1.0.0"}""");
    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variants");
  }

  @Test
  void rejectsBlankVariantKey() throws Exception {
    Path dir = pack("blank-variant-key");
    write(dir, "pack.json", """
        {"name":"blank-variant-key","version":"1.0.0","variants":{"":"c.md"}}""");
    write(dir, "c.md", "content");

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variants");
  }

  @Test
  void rejectsMissingVariantContentFile() throws Exception {
    Path dir = pack("missing-content");
    write(dir, "pack.json", """
        {"name":"missing-content","version":"1.0.0","variants":{"full":"absent.md"}}""");
    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content file not found");
  }

  @Test
  void rejectsVariantFileEscapingPackDirectory() throws Exception {
    Path dir = pack("traversal");
    write(root, "outside.md", "secret");
    write(dir, "pack.json", """
        {"name":"traversal","version":"1.0.0","variants":{"full":"../outside.md"}}""");
    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("escapes the pack directory");
  }

  @Test
  void rejectsDuplicatePackName() throws Exception {
    Path dirA = pack("a");
    write(dirA, "pack.json", """
        {"name":"dup","version":"1.0.0","variants":{"full":"c.md"}}""");
    write(dirA, "c.md", "one");
    Path dirB = pack("b");
    write(dirB, "pack.json", """
        {"name":"dup","version":"2.0.0","variants":{"full":"c.md"}}""");
    write(dirB, "c.md", "two");

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate context pack name 'dup'");
  }

  @Test
  void aggregatesSchemaValidationFailureWithOtherPackErrors() throws Exception {
    // A whitespace-only name is now caught at schema validation (context-pack.schema.json's
    // non-whitespace pattern), not at ContextPack's record constructor — this pins the aggregation
    // behaviour survives regardless of which layer catches the individual pack's failure.
    pack("no-manifest");
    Path dir = pack("blank-name");
    write(dir, "pack.json", """
        {"name":" ","version":"1.0.0","variants":{"full":"c.md"}}""");
    write(dir, "c.md", "content");

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing pack.json")
        .hasMessageContaining("/name: does not match the regex pattern");
  }

  @Test
  void rejectsWhitespaceOnlyVariantKeyAtSchemaValidation() throws Exception {
    // context-pack.schema.json's variants propertyNames pattern now rejects a whitespace-only key
    // itself, before ContextPackVariant's own Validate.notBlank would ever run — closing the gap
    // this test used to pin (schema passed, only the Java record constructor caught it).
    Path dir = pack("whitespace-variant-key");
    write(dir, "pack.json", """
        {"name":"whitespace-variant-key","version":"1.0.0","variants":{" ":"c.md"}}""");
    write(dir, "c.md", "content");

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/variants")
        .hasMessageContaining("does not match the regex pattern");
  }

  @Test
  void rejectsPackDirectorySymlinkEscapingContextPacksRoot(@TempDir Path outside) throws Exception {
    Path outsidePack = Files.createDirectories(outside.resolve("outside-pack"));
    write(outsidePack, "pack.json", """
        {"name":"outside","version":"1.0.0","variants":{"full":"c.md"}}""");
    write(outsidePack, "c.md", "content");

    Path link = root.resolve("linked-pack");
    try {
      Files.createSymbolicLink(link, outsidePack);
    } catch (IOException | UnsupportedOperationException e) {
      Assumptions.abort("Symlinks not supported in this environment: " + e.getMessage());
    }

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("escapes the context packs root");
  }

  @Test
  void rejectsVariantFileSymlinkEscapingPackDirectory(@TempDir Path outside) throws Exception {
    // The pack-directory symlink case is covered above; this pins the variant-FILE case — a
    // manifest naming an in-directory link whose target lives outside the pack.
    Path secret = outside.resolve("secret.md");
    Files.writeString(secret, "outside content");
    Path dir = pack("linked-variant");
    write(dir, "pack.json", """
        {"name":"linked-variant","version":"1.0.0","variants":{"full":"content.md"}}""");
    try {
      Files.createSymbolicLink(dir.resolve("content.md"), secret);
    } catch (IOException | UnsupportedOperationException e) {
      Assumptions.abort("Symlinks not supported in this environment: " + e.getMessage());
    }

    assertThatThrownBy(() -> loader().load())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("escapes the pack directory");
  }

  @Test
  void rejectsNonDirectoryRoot() {
    Path missing = root.resolve("does-not-exist");
    assertThatThrownBy(() -> new FileSystemContextPackLoader(mapper, schemaProvider, missing))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
