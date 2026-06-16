// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adversarial path-traversal tests for {@link Validate#requireWithinBase}.
 *
 * <p>The core safety invariant for the OSS framework is that untrusted relative paths (for
 * example, a path a model emits inside a {@code CREATE_FILE} command) can never resolve outside
 * the configured base directory. {@link ValidateTest.RequireWithinBaseTests} already covers the
 * baseline positive cases (direct child, nested child, child directory, {@code .}, the base
 * itself) and the common negative cases (relative traversal, absolute sibling outside base,
 * non-absolute base, null base, blank candidate, direct symlink escape). This class adds the
 * adversarial-encoding and edge scenarios that complement those: URL-encoded traversal literals,
 * embedded NUL bytes, an absolute system path, {@code ./child}, and a nested symlink escape.
 *
 * <p>Every negative case asserts the live exception type ({@link IllegalArgumentException}, or for
 * the NUL-byte case the {@link InvalidPathException} subclass the JDK path layer raises) rather
 * than any behaviour the implementation does not have. Filesystem-dependent cases (symlink, NUL
 * byte) are guarded with assumptions and never silently pass.
 */
class RequireWithinBaseSecurityTest {

  private static final char NUL = (char) 0;

  @TempDir
  Path tempDir;

  Path baseDir;

  @BeforeEach
  void setUp() throws IOException {
    baseDir = tempDir.resolve("base");
    Files.createDirectory(baseDir);
  }

  @Test
  void shouldTreatUrlEncodedTraversalAsLiteralSegmentInsideBase() {
    // %2e%2e%2f is the URL-encoding of "../". requireWithinBase performs no percent-decoding, so
    // these stay literal filename segments and the resolved path remains inside the base.
    Path result = Validate.requireWithinBase(baseDir, "%2e%2e%2fsecret.txt", "encoded traversal");

    Path realBase = realPath(baseDir);
    assertThat(result.startsWith(realBase)).isTrue();
    assertThat(result).isEqualTo(realBase.resolve("%2e%2e%2fsecret.txt"));
  }

  @Test
  void shouldTreatDoubleUrlEncodedTraversalAsLiteralSegmentInsideBase() {
    Path result = Validate.requireWithinBase(baseDir, "%2e%2e/%2e%2e/etc/passwd",
        "encoded traversal");

    assertThat(result.startsWith(realPath(baseDir))).isTrue();
  }

  @Test
  void shouldRejectAbsoluteSystemPathOutsideBase() {
    // Construct an OS-portable absolute path that lies outside the base. On POSIX this is an
    // /etc/passwd style path; on Windows it resolves against the root and still escapes the base.
    Path absoluteOutside = baseDir.getRoot().resolve("etc").resolve("passwd");

    assertThatThrownBy(
        () -> Validate.requireWithinBase(baseDir, absoluteOutside.toString(), "absolute escape"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("absolute escape");
  }

  @Test
  void shouldResolveDotSlashChildWithinBase() {
    Path result = Validate.requireWithinBase(baseDir, "./child.txt", "dot-slash child");

    Path realBase = realPath(baseDir);
    assertThat(result.startsWith(realBase)).isTrue();
    assertThat(result).isEqualTo(realBase.resolve("child.txt"));
  }

  @Test
  void shouldRejectEmbeddedNullByte() {
    // The JDK default filesystem rejects NUL bytes in path names with InvalidPathException (an
    // IllegalArgumentException subclass). Probe support first; skip with a reason if a filesystem
    // were ever to accept NUL, rather than asserting behaviour that does not exist there.
    assumeTrue(nullByteRejectedByFileSystem(),
        "Filesystem does not reject NUL bytes in path names");

    String candidate = "foo" + NUL + "/../bar";
    assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, candidate, "null byte"))
        .isInstanceOf(InvalidPathException.class);
  }

  @Test
  void shouldRejectNestedSymlinkEscape() throws IOException {
    // A symlinked directory inside the base that points outside it must not become a tunnel: a
    // candidate routed through the link has to be rejected by the per-segment symlink check.
    Path outside = tempDir.resolve("outside-target");
    Files.createDirectory(outside);
    Files.writeString(outside.resolve("loot.txt"), "secret");

    Path link = baseDir.resolve("linked");
    try {
      Files.createSymbolicLink(link, outside);
    } catch (IOException | UnsupportedOperationException e) {
      Assumptions.abort("Symlinks not supported in this environment: " + e.getMessage());
    }

    assertThatThrownBy(
        () -> Validate.requireWithinBase(baseDir, "linked/loot.txt", "nested symlink escape"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("nested symlink escape");
  }

  private static boolean nullByteRejectedByFileSystem() {
    try {
      Path.of("probe" + NUL + "name");
      return false;
    } catch (InvalidPathException e) {
      return true;
    }
  }

  private static Path realPath(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException("Could not resolve real path for test base: " + path, e);
    }
  }
}
