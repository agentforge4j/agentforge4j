// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClientConfiguration;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/**
 * Settings for invoking Anthropic Claude models through Amazon Bedrock.
 * <p>
 * Only {@code anthropic.*} Bedrock model IDs are supported by this adapter.
 */
public interface BedrockConfiguration extends LlmClientConfiguration {

  @Override
  default String getProviderName() {
    return "bedrock";
  }

  /**
   * AWS region for the Bedrock Runtime endpoint (for example {@code eu-west-1}).
   * <p>
   * When {@link #getEndpointOverride()} is set, the region is still used for SigV4 signing.
   */
  String getRegion();

  /**
   * When non-null, the Bedrock Runtime client uses this base URI instead of the public AWS
   * endpoint (for example a local test double or emulator). Production configurations should
   * leave this unset.
   */
  default URI getEndpointOverride() {
    return null;
  }

  /**
   * When non-null, the Bedrock Runtime client uses this credentials provider instead of
   * {@code DefaultCredentialsProvider}. Intended for tests and custom deployment wiring; leave
   * unset for normal AWS execution environments.
   */
  default AwsCredentialsProvider getCredentialsProvider() {
    return null;
  }

  /**
   * Bedrock Converse / Messages API version string sent in the request body (must be supplied by
   * application configuration).
   */
  String getAnthropicVersion();

  /**
   * Upper bound on a single {@code invokeModel} call, including retries inside the SDK.
   */
  Duration getRequestTimeout();

  /**
   * Maximum tokens to generate. When {@code null}, a module default is used.
   */
  default Integer getMaxTokens() {
    return null;
  }

  /**
   * Sampling temperature. When {@code null}, the field is omitted so the model uses its default.
   */
  default Double getTemperature() {
    return null;
  }
}
