// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.assertion;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Generic, reusable scanner asserting that a configurable set of forbidden terms is absent from a
 * tree of shipped OSS resource files (workflow definitions, agent bundles, prompts, verification
 * fixtures, examples). It complements the run-output check
 * {@link WorkflowRunAssert#outputsHaveNoForbiddenTerms(Collection)}: that one scans a run's context
 * and captured files, this one scans resource files on disk that never flow through a run.
 *
 * <p>Matching is case-insensitive and substring-based. Files are read as UTF-8 text; a file that is
 * not valid UTF-8 is skipped as non-text. Callers pass a {@code fileFilter} to scope the scan to the
 * text resource kinds they care about (for example {@code .json} / {@code .md}).
 */
public final class ForbiddenTermScanner {

  /**
   * One forbidden-term hit.
   *
   * @param file the offending file
   * @param term the forbidden term found
   * @param line the 1-based line number of the hit
   */
  public record Violation(Path file, String term, int line) {
  }

  private ForbiddenTermScanner() {
  }

  /**
   * Scans every regular file under {@code root} that matches {@code fileFilter} for any forbidden
   * term.
   *
   * @param root           the resource-tree root to scan; must not be {@code null}
   * @param forbiddenTerms the terms that must be absent; must not be empty
   * @param fileFilter     selects which files to scan; must not be {@code null}
   *
   * @return all violations found, in file then line order (empty when clean)
   */
  public static List<Violation> scan(Path root, Collection<String> forbiddenTerms,
      Predicate<Path> fileFilter) {
    Validate.notNull(root, "root must not be null");
    Validate.notEmpty(forbiddenTerms, "forbiddenTerms must not be empty");
    Validate.notNull(fileFilter, "fileFilter must not be null");
    List<Violation> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      List<Path> files = paths.filter(Files::isRegularFile).filter(fileFilter).sorted().toList();
      for (Path file : files) {
        scanFile(file, forbiddenTerms, violations);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to walk resource tree at " + root, exception);
    }
    return violations;
  }

  /**
   * Scans {@code root} and throws an {@link AssertionError} listing every violation when any forbidden
   * term is found.
   *
   * @param root           the resource-tree root to scan; must not be {@code null}
   * @param forbiddenTerms the terms that must be absent; must not be empty
   * @param fileFilter     selects which files to scan; must not be {@code null}
   */
  public static void assertNoForbiddenTerms(Path root, Collection<String> forbiddenTerms,
      Predicate<Path> fileFilter) {
    List<Violation> violations = scan(root, forbiddenTerms, fileFilter);
    if (!violations.isEmpty()) {
      throw new AssertionError(
          "Expected OSS resources to contain no forbidden terms but found: " + violations);
    }
  }

  private static void scanFile(Path file, Collection<String> forbiddenTerms,
      List<Violation> violations) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (MalformedInputException notText) {
      return;
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read resource file " + file, exception);
    }
    for (int index = 0; index < lines.size(); index++) {
      String lower = lines.get(index).toLowerCase(Locale.ROOT);
      for (String term : forbiddenTerms) {
        if (!term.isEmpty() && lower.contains(term.toLowerCase(Locale.ROOT))) {
          violations.add(new Violation(file, term, index + 1));
        }
      }
    }
  }
}
