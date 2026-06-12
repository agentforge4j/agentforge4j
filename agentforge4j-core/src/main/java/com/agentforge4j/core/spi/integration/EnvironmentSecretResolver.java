package com.agentforge4j.core.spi.integration;

import com.agentforge4j.util.Validate;
import org.apache.commons.lang3.StringUtils;

/**
 * OSS default {@link SecretResolver}: resolves a reference key against the process environment first and JVM system
 * properties second, so a reference such as {@code "GITHUB_TOKEN"} is read from {@link System#getenv(String)} or,
 * failing that, {@link System#getProperty(String)}. A reference with no value in either source is a fail-fast error
 * naming the reference key — never the value.
 *
 * <p>Stateless and thread-safe.
 */
public final class EnvironmentSecretResolver implements SecretResolver {

  @Override
  public String resolve(String reference) {
    Validate.notBlank(reference, "secret reference must not be blank");
    String value = System.getenv(reference);
    if (StringUtils.isBlank(value)) {
      value = System.getProperty(reference);
    }
    return Validate.notBlank(value,
        "No secret is configured for reference '%s' (checked environment variable then system property)"
            .formatted(reference));
  }
}
