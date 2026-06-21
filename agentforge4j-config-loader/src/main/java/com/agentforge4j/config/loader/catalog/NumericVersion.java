// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.catalog;

import com.agentforge4j.util.Validate;

/**
 * Numeric {@code major.minor.patch} version comparison for the catalog compatibility gate.
 *
 * <p>Only the numeric components are compared; any {@code -SNAPSHOT}, pre-release, or build
 * qualifier (everything from the first {@code -} or {@code +}) is stripped first. Consequently
 * {@code X-SNAPSHOT} compares equal to {@code X}, so a framework running {@code 0.0.1-SNAPSHOT}
 * satisfies a catalog minimum of {@code 0.0.1}. This is deliberately NOT strict semantic-version
 * ordering (which sorts a pre-release build before its release and would reject that case).
 *
 * <p>Fewer than three numeric components are zero-padded ({@code 1.2} is {@code 1.2.0}); more than
 * three are rejected as malformed rather than silently truncated, so a compatibility check never
 * passes on a version it did not fully understand.
 */
final class NumericVersion {

  private NumericVersion() {
  }

  /**
   * Compares two versions by numeric {@code major.minor.patch}, ignoring qualifiers.
   *
   * @param left  first version
   * @param right second version
   * @return negative, zero or positive as {@code left} is lower than, equal to, or higher than
   *         {@code right}
   */
  static int compare(String left, String right) {
    int[] leftParts = parse(left);
    int[] rightParts = parse(right);
    for (int i = 0; i < 3; i++) {
      int comparison = Integer.compare(leftParts[i], rightParts[i]);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  private static int[] parse(String version) {
    Validate.notBlank(version, "version must not be blank");
    String core = version.trim();
    int qualifier = indexOfQualifier(core);
    if (qualifier >= 0) {
      core = core.substring(0, qualifier);
    }
    String[] parts = core.split("\\.");
    if (parts.length > 3) {
      throw new CatalogCompatibilityException(
          "Version '%s' has more than three numeric components".formatted(version));
    }
    int[] numbers = new int[3];
    for (int i = 0; i < 3; i++) {
      numbers[i] = i < parts.length ? parsePart(parts[i], version) : 0;
    }
    return numbers;
  }

  private static int indexOfQualifier(String core) {
    int dash = core.indexOf('-');
    int plus = core.indexOf('+');
    if (dash < 0) {
      return plus;
    }
    if (plus < 0) {
      return dash;
    }
    return Math.min(dash, plus);
  }

  private static int parsePart(String part, String version) {
    try {
      return Integer.parseInt(part.trim());
    } catch (NumberFormatException e) {
      throw new CatalogCompatibilityException(
          "Unparseable numeric version component '%s' in '%s'".formatted(part, version), e);
    }
  }
}
