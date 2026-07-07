// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.testkit.assertion.ForbiddenTermScanner.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForbiddenTermScannerTest {

  private static final Predicate<Path> TEXT_RESOURCES = path -> {
    String name = path.getFileName().toString();
    return name.endsWith(".json") || name.endsWith(".md");
  };

  @Test
  void findsForbiddenTermsInMatchingFilesOnly(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("clean.json"), "{\"shape\":\"execution only\"}");
    Files.writeString(root.resolve("dirty.md"), "This costs $5 and is Billing related.");
    Files.writeString(root.resolve("ignored.txt"), "billing everywhere");

    List<Violation> violations = ForbiddenTermScanner.scan(root, Set.of("$", "billing"),
        TEXT_RESOURCES);

    assertThat(violations).extracting(Violation::term).contains("$", "billing");
    assertThat(violations).extracting(v -> v.file().getFileName().toString())
        .containsOnly("dirty.md");
  }

  @Test
  void assertNoForbiddenTermsThrowsListingTheHit(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("a.md"), "there is a credit here");

    assertThatThrownBy(
        () -> ForbiddenTermScanner.assertNoForbiddenTerms(root, Set.of("credit"), path -> true))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("credit");
  }

  @Test
  void assertNoForbiddenTermsPassesWhenClean(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("a.md"), "execution shape and token estimates only");

    assertThatCode(
        () -> ForbiddenTermScanner.assertNoForbiddenTerms(root, Set.of("$", "billing"), path -> true))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsEmptyTermSet(@TempDir Path root) {
    assertThatThrownBy(() -> ForbiddenTermScanner.scan(root, Set.of(), path -> true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
