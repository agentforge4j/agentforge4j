// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.agent;

import java.util.Map;

/**
 * Shared lookup for resolving a bundle-relative artifact file name (for example {@code agent.json})
 * within a captured-artifacts map keyed by the full {@code VALIDATE}-step path. The captured path may
 * be bundle-root-relative ({@code agent.json}) or rooted under an arbitrary prefix ({@code
 * shipped-agents/generated.agent/agent.json}), so lookups match the bare name or its last path
 * segment rather than requiring an exact key.
 */
final class BundleArtifactPaths {

  private BundleArtifactPaths() {
    throw new UnsupportedOperationException("static utility");
  }

  /**
   * Finds the captured-artifact key that resolves the given bundle-relative file name. Assumes
   * {@code artifacts} is scoped to a single bundle (as {@code ValidateBehaviourHandler} always
   * scopes it to one step's own {@code requiredArtifacts}), so exactly zero or one key is expected
   * to match — if more than one key matches, resolution is ambiguous and this fails closed rather
   * than silently picking one.
   *
   * @param artifacts captured artifacts keyed by their full VALIDATE-step path
   * @param fileName  bundle-relative file name to resolve, for example {@code agent.json}
   *
   * @return the matching key, or {@code null} when no artifact resolves the file name
   *
   * @throws IllegalStateException if more than one key resolves the file name
   */
  static String findKey(Map<String, String> artifacts, String fileName) {
    String matched = null;
    for (String key : artifacts.keySet()) {
      if (key.equals(fileName) || key.endsWith("/" + fileName)) {
        if (matched != null) {
          throw new IllegalStateException(
              "ambiguous artifact match for '%s': both '%s' and '%s' resolve it"
                  .formatted(fileName, matched, key));
        }
        matched = key;
      }
    }
    return matched;
  }

  /**
   * Derives the bundle-root prefix (everything up to and including the final path separator, empty
   * for a bundle-root-relative key) from a key matched by {@link #findKey}, so sibling bundle-relative
   * file names can be resolved via {@code prefix + siblingFileName}.
   *
   * @param matchedKey key previously returned by {@link #findKey}
   * @param fileName   the same file name passed to {@link #findKey}
   *
   * @return the bundle-root prefix, possibly empty
   */
  static String prefixOf(String matchedKey, String fileName) {
    return matchedKey.substring(0, matchedKey.length() - fileName.length());
  }
}
