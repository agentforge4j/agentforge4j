// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.execution.behaviour.resource;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Safe resolver for classpath resources under explicit allow-listed roots.
 */
public final class SafeClasspathResourceResolver implements ResourceResolver {

  private static final List<String> ALLOWED_ROOTS = List.of(
      "/workflow-resources/",
      "/schemas/",
      "/templates/",
      "/examples/",
      "/schema/");
  private final ClassLoader classLoader = resolveClassLoader();

  @Override
  public String resolve(String resourcePath) {
    String normalizedPath = normalizePath(resourcePath);
    String classpathPath = normalizedPath.substring(1);
    try (InputStream stream = classLoader.getResourceAsStream(classpathPath)) {
      Validate.notNull(stream,
          "Resource not found under allowed roots: %s".formatted(normalizedPath));
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to read classpath resource: %s".formatted(normalizedPath), exception);
    }
  }

  private String normalizePath(String resourcePath) {
    Validate.notBlank(resourcePath, "resourcePath must not be blank");
    for (int i = 0; i < resourcePath.length(); i++) {
      char c = resourcePath.charAt(i);
      if (c < 0x20 || c == 0x7f) {
        throw new IllegalArgumentException(
            "resourcePath contains illegal control characters: %s".formatted(resourcePath));
      }
    }
    String trimmed = resourcePath.strip();
    String probe = trimmed.toLowerCase(Locale.ROOT);
    if (probe.startsWith("jar:")
        || probe.startsWith("file:")
        || probe.startsWith("http:")
        || probe.startsWith("https:")) {
      throw new IllegalArgumentException("resourcePath must not use URI-style classpath references");
    }

    String slashNormalized = trimmed.replace('\\', '/');
    if (slashNormalized.length() >= 2
        && Character.isLetter(slashNormalized.charAt(0))
        && slashNormalized.charAt(1) == ':') {
      throw new IllegalArgumentException("resourcePath absolute filesystem paths are not allowed");
    }
    Validate.isTrue(slashNormalized.startsWith("/"),
        "resourcePath must start with '/': %s".formatted(resourcePath));

    ArrayDeque<String> stack = new ArrayDeque<>();
    for (String rawSegment : slashNormalized.split("/")) {
      if (rawSegment.isEmpty() || ".".equals(rawSegment)) {
        continue;
      }
      String segment;
      try {
        segment = URLDecoder.decode(rawSegment, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "resourcePath has malformed encoding in segment: %s".formatted(rawSegment), e);
      }
      if (".".equals(segment)) {
        continue;
      }
      if ("..".equals(segment)) {
        if (stack.isEmpty()) {
          throw new IllegalArgumentException("resourcePath traversal is not allowed");
        }
        stack.removeLast();
        continue;
      }
      stack.addLast(segment);
    }

    String normalized = "/" + String.join("/", stack);
    boolean allowed = ALLOWED_ROOTS.stream().anyMatch(normalized::startsWith);
    Validate.isTrue(allowed, "resourcePath is outside allowed roots: %s".formatted(resourcePath));

    return normalized;
  }

  private static ClassLoader resolveClassLoader() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    return contextClassLoader != null
        ? contextClassLoader
        : SafeClasspathResourceResolver.class.getClassLoader();
  }
}
