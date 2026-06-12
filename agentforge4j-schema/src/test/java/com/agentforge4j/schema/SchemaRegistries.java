package com.agentforge4j.schema;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.InputStreamSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared registry construction for the contract tests. Draft 2020-12 default dialect plus a
 * {@code file:} resource loader that resolves through {@link Path} — the stock URL-connection
 * loader does not percent-decode {@code file:} IRIs, which breaks cross-schema {@code $ref}
 * resolution (e.g. {@code blueprint.schema.json} ↔ {@code workflow.schema.json}) when the repo
 * is checked out under a path containing spaces.
 */
final class SchemaRegistries {

  private SchemaRegistries() {
  }

  static SchemaRegistry draft202012() {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
        builder -> builder.resourceLoaders(
            loaders -> loaders.values(list -> list.add(0, SchemaRegistries::loadFileIri))));
  }

  private static InputStreamSource loadFileIri(AbsoluteIri iri) {
    if (!"file".equals(iri.getScheme())) {
      return null;
    }
    Path path = Path.of(URI.create(iri.toString()));
    return () -> Files.newInputStream(path);
  }
}
