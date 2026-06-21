// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verifies the built catalog artifact is resources-only: it contains no compiled classes and
 * declares no runtime {@code Class-Path}, carrying only the expected automatic module name. Runs in
 * the integration-test phase (Failsafe), after the jar is packaged, and fails the build on
 * violation.
 */
class CatalogArtifactResourcesOnlyIT {

  @Test
  void artifactIsResourcesOnlyWithNoClassPath() throws IOException {
    Path jar = locateModuleJar();
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      List<String> classEntries = jarFile.stream()
          .map(JarEntry::getName)
          .filter(name -> name.endsWith(".class"))
          .toList();
      assertThat(classEntries)
          .as("catalog artifact must contain zero .class files")
          .isEmpty();

      Manifest manifest = jarFile.getManifest();
      Attributes mainAttributes = manifest.getMainAttributes();
      assertThat(mainAttributes.getValue("Class-Path"))
          .as("catalog artifact manifest must not declare a Class-Path")
          .isNull();
      assertThat(mainAttributes.getValue("Automatic-Module-Name"))
          .as("catalog artifact must carry the automatic module name")
          .isEqualTo("agentforge4j.workflows.catalog");
    }
  }

  private static Path locateModuleJar() throws IOException {
    Path target = Path.of("target");
    try (Stream<Path> jars = Files.list(target)) {
      return jars
          .filter(path -> {
            String name = path.getFileName().toString();
            return name.startsWith("agentforge4j-workflows-catalog")
                && name.endsWith(".jar")
                && !name.endsWith("-sources.jar")
                && !name.endsWith("-javadoc.jar");
          })
          .findFirst()
          .orElseThrow(() -> new UncheckedIOException(new IOException(
              "Catalog artifact jar not found under " + target.toAbsolutePath())));
    }
  }
}
