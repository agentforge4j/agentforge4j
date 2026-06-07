package com.agentforge4j.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * Property-based fuzzing of {@link Validate#requireWithinBase} to complement the hand-picked
 * example cases in {@code ValidateTest} and {@code RequireWithinBaseSecurityTest}.
 *
 * <p>Invariant: for any sequence of path segments (mixing {@code ..}, {@code .}, and random safe
 * names) resolved under a base directory, {@code requireWithinBase} either returns a path that is
 * <strong>always</strong> inside the base, or it throws - it never returns a path that escapes the
 * base. Named {@code *Test} so the project's Surefire include pattern ({@code **&#47;*Test.java})
 * discovers it; the jqwik engine runs the {@code @Property} methods.
 */
class RequireWithinBasePropertyTest {

  private Path base;
  private Path realBase;

  @BeforeProperty
  void createBase() throws IOException {
    base = Files.createTempDirectory("rwb-prop-base");
    realBase = base.toRealPath();
  }

  @AfterProperty
  void deleteBase() throws IOException {
    if (base != null) {
      Files.deleteIfExists(base);
    }
  }

  @Property(tries = 300)
  void resolvedPathNeverEscapesBase(@ForAll("pathSegments") List<String> segments) {
    String relativePath = String.join("/", segments);
    Assume.that(!relativePath.isBlank());

    try {
      Path resolved = Validate.requireWithinBase(base, relativePath, "escape");
      // On success the resolved path must lie within the (real) base directory.
      assertThat(resolved.normalize().startsWith(realBase)).isTrue();
    } catch (IllegalArgumentException rejected) {
      // Acceptable: an escaping or otherwise invalid candidate is rejected by throwing the guard's
      // documented type. IllegalArgumentException also covers InvalidPathException (its subclass,
      // raised by path construction). Any other exception (e.g. NPE) propagates and fails the
      // property, matching the rigor of LlmCommandParserPropertyTest.
    }
  }

  @Provide
  Arbitrary<List<String>> pathSegments() {
    Arbitrary<String> segment = Arbitraries.oneOf(
        Arbitraries.of("..", ".", "sub", "nested"),
        Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(8));
    return segment.list().ofMinSize(1).ofMaxSize(6);
  }
}
