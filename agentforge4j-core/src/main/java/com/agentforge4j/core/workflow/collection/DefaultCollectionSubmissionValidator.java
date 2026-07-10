// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.collection;

/**
 * Shipped {@link CollectionSubmissionValidator} that guards runtime integrity only: it denies
 * payload {@link FileRef}s whose path is absolute or escapes upward, since a stored path like
 * {@code ../..} corrupts whatever file layout the embedding application materialises the
 * collection into. Everything else is admitted — client-token conventions, payload content rules,
 * and any deployment-specific policy belong to the embedding application's own validator, not to
 * the framework.
 */
public final class DefaultCollectionSubmissionValidator implements CollectionSubmissionValidator {

  /** Creates the default validator. */
  public DefaultCollectionSubmissionValidator() {
  }

  @Override
  public Decision validate(CollectionSubmissionContext context) {
    for (FileRef file : context.payload().files()) {
      if (isUnsafePath(file.path())) {
        return Decision.deny(
            "FILE_PATH_UNSAFE: file path '%s' must be relative and must not escape upward"
                .formatted(file.path()));
      }
    }
    return Decision.allow();
  }

  /**
   * Whether the path is absolute, drive- or scheme-qualified, contains an upward traversal
   * segment, or embeds a NUL character. Checked lexically on both separator conventions — the
   * framework never resolves the path itself, so this guards the stored value, not a filesystem
   * access.
   */
  private static boolean isUnsafePath(String path) {
    if (path.indexOf('\0') >= 0) {
      return true;
    }
    String normalised = path.replace('\\', '/');
    if (normalised.startsWith("/") || normalised.contains(":")) {
      return true;
    }
    for (String segment : normalised.split("/", -1)) {
      if ("..".equals(segment)) {
        return true;
      }
    }
    return false;
  }
}
