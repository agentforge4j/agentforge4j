package com.agentforge4j.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Provides static methods for validating arguments and paths.
 */
public final class Validate {

  private Validate() {
  }

  /**
   * Validates that the given string is not blank.
   *
   * @param value   the string to validate
   * @param message the exception message if validation fails
   * @return the validated string
   * @throws IllegalArgumentException if the string is blank
   */
  public static String notBlank(String value, String message) {
    return notBlank(value, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given string is not blank.
   *
   * @param value             the string to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated string
   */
  public static String notBlank(String value, Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    if (isBlank(value)) {
      throw exceptionSupplier.get();
    }
    return value;
  }

  /**
   * Validates that the given value is not null.
   *
   * @param value   the value to validate
   * @param message the exception message if validation fails
   * @return the validated value
   * @throws IllegalArgumentException if the value is null
   */
  public static <T> T notNull(T value, String message) {
    return notNull(value, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given value is not null.
   *
   * @param value             the value to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated value
   */
  public static <T> T notNull(T value, Supplier<RuntimeException> exceptionSupplier) {
    if (exceptionSupplier == null) {
      throw new IllegalArgumentException("Exception supplier must not be null");
    }
    if (value == null) {
      throw exceptionSupplier.get();
    }
    return value;
  }

  /**
   * Validates that the given condition is true.
   *
   * @param condition the condition to validate
   * @param message   the exception message if validation fails
   * @throws IllegalArgumentException if the condition is false
   */
  public static void isTrue(boolean condition, String message) {
    isTrue(condition, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given condition is true.
   *
   * @param condition         the condition to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   */
  public static void isTrue(boolean condition, Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    if (!condition) {
      throw exceptionSupplier.get();
    }
  }

  /**
   * Validates that the given collection is not empty.
   *
   * @param collection the collection to validate
   * @param message    the exception message if validation fails
   * @return the validated collection
   * @throws IllegalArgumentException if the collection is null or empty
   */
  public static <T extends Collection<?>> T notEmpty(final T collection, final String message) {
    return notEmpty(collection, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given collection is not empty.
   *
   * @param collection        the collection to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated collection
   */
  public static <T extends Collection<?>> T notEmpty(final T collection,
      Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    if (collection == null || collection.isEmpty()) {
      throw exceptionSupplier.get();
    }
    return collection;
  }

  /**
   * Validates that the resolved path stays within the base directory.
   *
   * @param base         the absolute base directory path
   * @param relativePath the relative path to resolve
   * @param message      the exception message if validation fails
   * @return the resolved and validated absolute path
   * @throws IllegalArgumentException if the base is not absolute, or the resolved path is outside the base
   */
  public static Path requireWithinBase(Path base, String relativePath, String message) {
    return requireWithinBase(base, relativePath, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the resolved path stays within the base directory.
   *
   * @param base              the absolute base directory path
   * @param relativePath      the relative path to resolve
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the resolved and validated absolute path
   */
  public static Path requireWithinBase(Path base, String relativePath,
      Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    notNull(base, "Base path must not be null");
    isTrue(base.isAbsolute(), "Base path must be absolute");
    notBlank(relativePath, "Relative path must not be blank");
    final Path realBase;
    try {
      realBase = base.normalize().toRealPath();
    } catch (IOException e) {
      throw new IllegalArgumentException("Base path could not be resolved: " + base, e);
    }
    Path resolvedPath = realBase.resolve(relativePath).normalize();
    if (!resolvedPath.startsWith(realBase)) {
      throw exceptionSupplier.get();
    }
    try {
      ensureNoSymlinkEscape(realBase, resolvedPath, exceptionSupplier);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Resolved path could not be verified: " + relativePath, e);
    }
    if (Files.exists(resolvedPath)) {
      try {
        return resolvedPath.toRealPath();
      } catch (IOException e) {
        throw new IllegalArgumentException(
            "Resolved path could not be resolved: " + relativePath, e);
      }
    }
    return resolvedPath;
  }

  /**
   * Validates that the given path is a directory.
   *
   * @param path    the path to validate
   * @param message the exception message if validation fails
   * @return the validated directory path
   * @throws IllegalArgumentException if the path is not a directory
   */
  public static Path requireDirectory(Path path, String message) {
    return requireDirectory(path, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given path is a directory.
   *
   * @param path              the path to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated directory path
   */
  public static Path requireDirectory(Path path, Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    notNull(path, "Directory path must not be null");
    isTrue(Files.isDirectory(path), exceptionSupplier);
    return path.toAbsolutePath().normalize();
  }

  /**
   * Validates that the given value is between the specified bounds.
   *
   * @param lower   the lower bound (inclusive)
   * @param upper   the upper bound (inclusive)
   * @param value   the value to validate
   * @param message the exception message if validation fails
   * @throws IllegalArgumentException if the value is outside the bounds
   */
  public static void isBetween(Number lower, Number upper, Number value, String message) {
    isBetween(lower, upper, value, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given value is between the specified bounds.
   *
   * @param lower             the lower bound (inclusive)
   * @param upper             the upper bound (inclusive)
   * @param value             the value to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @throws IllegalArgumentException if the value is outside the bounds
   */
  public static void isBetween(Number lower, Number upper, Number value,
      Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    notNull(lower, exceptionSupplier);
    notNull(upper, exceptionSupplier);
    notNull(value, exceptionSupplier);
    isTrue(lower.doubleValue() < upper.doubleValue(),
        "Lower value should be less than upper value");
    isTrue(
        value.doubleValue() >= lower.doubleValue() && value.doubleValue() <= upper.doubleValue(),
        exceptionSupplier);
  }

  /**
   * Validates that the given value is greater than zero.
   *
   * @param value   the value to validate
   * @param message the exception message if validation fails
   * @return the validated value
   * @throws IllegalArgumentException if the value is not greater than zero
   */
  public static Number isGreaterThanZero(Number value, String message) {
    return isGreaterThanZero(value, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given value is greater than zero.
   *
   * @param value             the value to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated value
   */
  public static Number isGreaterThanZero(Number value,
      Supplier<RuntimeException> exceptionSupplier) {
    return isGreaterThan(value, 0, exceptionSupplier);
  }

  /**
   * Validates that the given value is not negative.
   *
   * @param value   the value to validate
   * @param message the exception message if validation fails
   * @return the validated value
   * @throws IllegalArgumentException if the value is negative
   */
  public static Number isNotNegative(Number value, String message) {
    return isNotNegative(value, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given value is not negative.
   *
   * @param value             the value to validate
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated value
   */
  public static Number isNotNegative(Number value, Supplier<RuntimeException> exceptionSupplier) {
    return isGreaterThan(value, -1, exceptionSupplier);
  }

  /**
   * Validates that the given value is greater than the specified lower bound.
   *
   * @param value   the value to validate
   * @param lower   the lower bound (exclusive)
   * @param message the exception message if validation fails
   * @return the validated value
   * @throws IllegalArgumentException if the value is not greater than the lower bound
   */
  public static Number isGreaterThan(Number value, Number lower, String message) {
    return isGreaterThan(value, lower, () -> new IllegalArgumentException(message));
  }

  /**
   * Validates that the given value is greater than the specified lower bound.
   *
   * @param value             the value to validate
   * @param lower             the lower bound (exclusive)
   * @param exceptionSupplier supplies the exception to throw if validation fails
   * @return the validated value
   */
  public static Number isGreaterThan(Number value, Number lower,
      Supplier<RuntimeException> exceptionSupplier) {
    notNull(exceptionSupplier, "Exception supplier must not be null");
    notNull(value, exceptionSupplier);
    notNull(lower, exceptionSupplier);
    isTrue(value.doubleValue() > lower.doubleValue(), exceptionSupplier);
    return value;
  }

  private static boolean isBlank(String value) {
    if (value == null || value.isEmpty()) {
      return true;
    }
    return value.codePoints().allMatch(
        cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
  }

  private static void ensureNoSymlinkEscape(Path realBase, Path resolvedPath,
      Supplier<RuntimeException> exceptionSupplier) throws IOException {
    Path current = realBase;
    for (int i = realBase.getNameCount(); i < resolvedPath.getNameCount(); i++) {
      current = current.resolve(resolvedPath.getName(i));
      if (Files.exists(current) || Files.isSymbolicLink(current)) {
        current = current.toRealPath();
        if (!current.startsWith(realBase)) {
          throw exceptionSupplier.get();
        }
      }
    }
  }
}
