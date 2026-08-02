// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateTest {

  @TempDir
  Path tempDir;

  Path baseDir;
  Path subDir;
  Path fileInBase;

  @BeforeEach
  void setUp() throws Exception {
    baseDir = tempDir.resolve("base");
    Files.createDirectory(baseDir);
    subDir = baseDir.resolve("sub");
    Files.createDirectory(subDir);
    fileInBase = baseDir.resolve("file.txt");
    Files.writeString(fileInBase, "content");
  }

  @Nested
  class NotBlankTests {

    @Test
    void shouldReturnValueWhenNotBlank() {
      String result = Validate.notBlank("test", "message");
      assertThat(result).isEqualTo("test");
    }

    @Test
    void shouldThrowWhenNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.notBlank(null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNullWithSupplier() {
      assertThatThrownBy(() -> Validate.notBlank(null, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenEmptyWithStringMessage() {
      assertThatThrownBy(() -> Validate.notBlank("", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenEmptyWithSupplier() {
      assertThatThrownBy(() -> Validate.notBlank("", () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenWhitespaceWithStringMessage() {
      assertThatThrownBy(() -> Validate.notBlank("   ", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenWhitespaceWithSupplier() {
      assertThatThrownBy(() -> Validate.notBlank("   ", () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenTabAndNewlineOnly() {
      assertThatThrownBy(() -> Validate.notBlank("\t\n", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNonBreakingSpaceOnly() {
      assertThatThrownBy(() -> Validate.notBlank("\u00a0", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldReturnSameInstanceWithSupplierOverload() {
      String value = "ok";
      assertThat(Validate.notBlank(value, () -> new RuntimeException("unused"))).isSameAs(value);
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.notBlank("test", (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }

  @Nested
  class NotNullTests {

    @Test
    void shouldReturnValueWhenNotNull() {
      Object result = Validate.notNull("test", "message");
      assertThat(result).isEqualTo("test");
    }

    @Test
    void shouldThrowWhenNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.notNull(null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNullWithSupplier() {
      assertThatThrownBy(() -> Validate.notNull(null, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.notNull("test", (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }

  @Nested
  class IsTrueTests {

    @Test
    void shouldNotThrowWhenTrue() {
      Validate.isTrue(true, "message");
    }

    @Test
    void shouldThrowWhenFalseWithStringMessage() {
      assertThatThrownBy(() -> Validate.isTrue(false, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenFalseWithSupplier() {
      assertThatThrownBy(() -> Validate.isTrue(false, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.isTrue(true, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNullEvenIfConditionFalse() {
      assertThatThrownBy(() -> Validate.isTrue(false, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }

  @Nested
  class NotEmptyTests {

    @Test
    void shouldReturnCollectionWhenNotEmpty() {
      List<String> list = List.of("item");
      List<String> result = Validate.notEmpty(list, "message");
      assertThat(result).isEqualTo(list);
    }

    @Test
    void shouldThrowWhenNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.notEmpty((List<String>) null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNullWithSupplier() {
      assertThatThrownBy(
          () -> Validate.notEmpty((List<String>) null, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenEmptyWithStringMessage() {
      assertThatThrownBy(() -> Validate.notEmpty(List.of(), "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenEmptyWithSupplier() {
      assertThatThrownBy(() -> Validate.notEmpty(List.of(), () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenEmptyMutableCollection() {
      assertThatThrownBy(() -> Validate.notEmpty(new ArrayList<String>(), "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldReturnSameCollectionInstanceWhenNotEmpty() {
      List<String> list = new ArrayList<>(List.of("a"));
      assertThat(Validate.notEmpty(list, "message")).isSameAs(list);
    }

    @Test
    void shouldReturnNonListCollectionWhenNotEmpty() {
      Set<String> set = new LinkedHashSet<>(List.of("only"));
      assertThat(Validate.notEmpty(set, "message")).isSameAs(set);
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.notEmpty(List.of("item"), (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }

  @Nested
  class RequireWithinBaseTests {

    // requireWithinBase returns the canonical real path (toRealPath) for an existing resolved
    // target, so expectations compare against toRealPath rather than toAbsolutePath().normalize().
    // The two coincide on most paths, but diverge where the temp directory sits under a Windows
    // 8.3 short name (e.g. a CI runner's RUNNER~1), which toRealPath expands to its long form.

    @Test
    void shouldResolveWithinBase() throws IOException {
      Path result = Validate.requireWithinBase(baseDir, "file.txt", "message");
      assertThat(result).isEqualTo(fileInBase.toRealPath());
    }

    @Test
    void shouldResolveSubPathWithinBase() throws IOException {
      Path result = Validate.requireWithinBase(baseDir, "sub", "message");
      assertThat(result).isEqualTo(subDir.toRealPath());
    }

    @Test
    void shouldResolveDotToBaseDirectory() throws IOException {
      Path result = Validate.requireWithinBase(baseDir, ".", "message");
      assertThat(result).isEqualTo(baseDir.toRealPath());
    }

    @Test
    void shouldResolveRedundantSegmentsWithinBase() throws IOException {
      Path result = Validate.requireWithinBase(baseDir, "sub/../file.txt", "message");
      assertThat(result).isEqualTo(fileInBase.toRealPath());
    }

    @Test
    void shouldThrowWhenPathTraversalWithStringMessage() {
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "../outside", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNestedPathTraversalEscapesBase() {
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "sub/../../outside", "nested escape"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("nested escape");
    }

    @Test
    void shouldThrowWhenPathTraversalWithSupplier() {
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "../outside",
          () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenBaseNull() {
      assertThatThrownBy(() -> Validate.requireWithinBase(null, "file.txt", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Base path must not be null");
    }

    @Test
    void shouldThrowWhenBaseNotAbsolute() {
      assertThatThrownBy(() -> Validate.requireWithinBase(Path.of("relative-base"), "file.txt", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Base path must be absolute");
    }

    @Test
    void shouldThrowWhenSymlinkPointsOutsideBase() throws Exception {
      Path outside = tempDir.resolve("outside-symlink-target");
      Files.createDirectory(outside);
      Path link = baseDir.resolve("escape-link");
      try {
        Files.createSymbolicLink(link, outside);
      } catch (IOException | UnsupportedOperationException e) {
        Assumptions.abort("Symlinks not supported in this environment: " + e.getMessage());
      }
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "escape-link", "symlink escape"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("symlink escape");
    }

    @Test
    void shouldThrowWhenRelativePathBlank() {
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Relative path must not be blank");
    }

    @Test
    void shouldThrowWhenRelativePathWhitespaceOnly() {
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "   ", "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Relative path must not be blank");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.requireWithinBase(baseDir, "file.txt", (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }

    @Test
    void shouldThrowWhenRelativePathIsAbsoluteOutsideBase() throws Exception {
      Path siblingOutsideBase = tempDir.resolve("outside-base-sibling.txt");
      Files.writeString(siblingOutsideBase, "x");
      assertThatThrownBy(
          () -> Validate.requireWithinBase(baseDir, siblingOutsideBase.toAbsolutePath().toString(), "escape"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("escape");
    }
  }

  @Nested
  class RequireDirectoryTests {

    @Test
    void shouldReturnNormalizedPathWhenDirectory() {
      Path result = Validate.requireDirectory(baseDir, "message");
      assertThat(result).isEqualTo(baseDir.toAbsolutePath().normalize());
    }

    @Test
    void shouldThrowWhenNotDirectoryWithStringMessage() {
      assertThatThrownBy(() -> Validate.requireDirectory(fileInBase, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNotDirectoryWithSupplier() {
      assertThatThrownBy(
          () -> Validate.requireDirectory(fileInBase, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenPathDoesNotExistWithStringMessage() {
      Path missing = baseDir.resolve("no-such-dir");
      assertThatThrownBy(() -> Validate.requireDirectory(missing, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenPathDoesNotExistWithSupplier() {
      Path missing = baseDir.resolve("missing-subdir");
      assertThatThrownBy(
          () -> Validate.requireDirectory(missing, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.requireDirectory(null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Directory path must not be null");
    }

    @Test
    void shouldThrowWhenNullWithSupplier() {
      assertThatThrownBy(
          () -> Validate.requireDirectory(null, () -> new RuntimeException("custom")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Directory path must not be null");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.requireDirectory(baseDir, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }

  @Nested
  class IsBetweenTests {

    @Test
    void shouldNotThrowWhenWithinBounds() {
      Validate.isBetween(1, 10, 5, "message");
    }

    @Test
    void shouldNotThrowWhenAtLowerBound() {
      Validate.isBetween(1, 10, 1, "message");
    }

    @Test
    void shouldNotThrowWhenAtUpperBound() {
      Validate.isBetween(1, 10, 10, "message");
    }

    @Test
    void shouldThrowWhenBelowLowerBoundWithStringMessage() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, 0, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenBelowLowerBoundWithSupplier() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, 0, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenAboveUpperBoundWithStringMessage() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, 11, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenAboveUpperBoundWithSupplier() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, 11, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldWorkWithDoubleValues() {
      Validate.isBetween(1.0, 10.0, 5.5, "message");
    }

    @Test
    void shouldWorkWithMixedNumberTypes() {
      Validate.isBetween(1, 10.0, 5, "message");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, 5, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }

    @Test
    void shouldThrowWhenLowerEqualsUpperBeforeValueCheck() {
      assertThatThrownBy(() -> Validate.isBetween(5, 5, 5, "out of range"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lower value should be less than upper value");
    }

    @Test
    void shouldThrowWhenLowerGreaterThanUpperWithFixedMessageRegardlessOfSupplier() {
      assertThatThrownBy(
          () -> Validate.isBetween(10, 1, 5, () -> new RuntimeException("custom")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Lower value should be less than upper value");
    }

    @Test
    void shouldThrowWhenLowerNull() {
      assertThatThrownBy(() -> Validate.isBetween(null, 10, 5, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenUpperNull() {
      assertThatThrownBy(() -> Validate.isBetween(1, null, 5, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenValueNull() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenValueIsNaN() {
      assertThatThrownBy(() -> Validate.isBetween(1, 10, Double.NaN, "not a number"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("not a number");
    }

    @Test
    void shouldWorkWithLongBounds() {
      Validate.isBetween(1L, 10L, 7L, "message");
    }
  }

  @Nested
  class IsNotNegativeTests {

    @Test
    void shouldReturnValueWhenZero() {
      Number result = Validate.isNotNegative(0, "message");
      assertThat(result).isEqualTo(0);
    }

    @Test
    void shouldReturnValueWhenPositive() {
      Number result = Validate.isNotNegative(42, "message");
      assertThat(result).isEqualTo(42);
    }

    @Test
    void shouldReturnValueWithSupplierOverload() {
      assertThat(Validate.isNotNegative(0.5, () -> new RuntimeException("custom"))).isEqualTo(0.5);
    }

    @Test
    void shouldThrowWhenNegativeOneWithStringMessage() {
      assertThatThrownBy(() -> Validate.isNotNegative(-1, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNegativeWithStringMessage() {
      assertThatThrownBy(() -> Validate.isNotNegative(-2, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNegativeWithSupplier() {
      assertThatThrownBy(() -> Validate.isNotNegative(-2, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenFractionalNegativeWithStringMessage() {
      assertThatThrownBy(() -> Validate.isNotNegative(-0.5, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenValueNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.isNotNegative(null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenValueNullWithSupplier() {
      assertThatThrownBy(() -> Validate.isNotNegative(null, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.isNotNegative(0, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }

  @Nested
  class IsGreaterThanZeroTests {

    @Test
    void shouldReturnValueWhenGreaterThanZero() {
      Number result = Validate.isGreaterThanZero(5, "message");
      assertThat(result).isEqualTo(5);
    }

    @Test
    void shouldReturnValueWhenGreaterThanZeroWithDouble() {
      Number result = Validate.isGreaterThanZero(5.5, "message");
      assertThat(result).isEqualTo(5.5);
    }

    @Test
    void shouldThrowWhenZeroWithStringMessage() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(0, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenZeroWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(0, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenNegativeWithStringMessage() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(-1, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenNegativeWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(-1.5, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(5, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }

    @Test
    void shouldThrowWhenValueNull() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenValueNullWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThanZero(null, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }
  }

  @Nested
  class IsGreaterThanTests {

    @Test
    void shouldReturnValueWhenStrictlyGreaterThanLower() {
      assertThat(Validate.isGreaterThan(5, 3, "message")).isEqualTo(5);
    }

    @Test
    void shouldReturnValueWhenStrictlyGreaterWithDouble() {
      assertThat(Validate.isGreaterThan(3.1, 3.0, "message")).isEqualTo(3.1);
    }

    @Test
    void shouldReturnValueWithSupplierOverload() {
      assertThat(Validate.isGreaterThan(2, 1, () -> new RuntimeException("custom"))).isEqualTo(2);
    }

    @Test
    void shouldThrowWhenEqualToLowerWithStringMessage() {
      assertThatThrownBy(() -> Validate.isGreaterThan(5, 5, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenEqualToLowerWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThan(5, 5, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenLessThanLowerWithStringMessage() {
      assertThatThrownBy(() -> Validate.isGreaterThan(2, 10, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenLessThanLowerWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThan(1.0, 2.0, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenValueNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.isGreaterThan(null, 0, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenLowerNullWithStringMessage() {
      assertThatThrownBy(() -> Validate.isGreaterThan(5, null, "message"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("message");
    }

    @Test
    void shouldThrowWhenValueNullWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThan(null, 0, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenLowerNullWithSupplier() {
      assertThatThrownBy(() -> Validate.isGreaterThan(5, null, () -> new RuntimeException("custom")))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("custom");
    }

    @Test
    void shouldThrowWhenExceptionSupplierNull() {
      assertThatThrownBy(() -> Validate.isGreaterThan(5, 3, (Supplier<RuntimeException>) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Exception supplier must not be null");
    }
  }
}
