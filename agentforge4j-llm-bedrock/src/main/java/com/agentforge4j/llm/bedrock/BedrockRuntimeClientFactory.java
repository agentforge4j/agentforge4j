package com.agentforge4j.llm.bedrock;

import com.agentforge4j.util.Validate;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

final class BedrockRuntimeClientFactory {

  private BedrockRuntimeClientFactory() {
  }

  static BedrockRuntimeClient create(BedrockConfiguration config) {
    Validate.notNull(config, "config must not be null");
    String region = Validate.notBlank(config.getRegion(), "Bedrock region must be provided");
    Duration requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Bedrock requestTimeout must not be null");
    Duration connectTimeout = Validate.notNull(config.getConnectTimeout(),
        "Bedrock connectTimeout must not be null");

    ClientOverrideConfiguration override = ClientOverrideConfiguration.builder()
        .apiCallTimeout(requestTimeout)
        .build();

    SdkHttpClient httpClient = UrlConnectionHttpClient.builder()
        .connectionTimeout(connectTimeout)
        .socketTimeout(requestTimeout)
        .build();

    AwsCredentialsProvider credentials = config.getCredentialsProvider();
    if (credentials == null) {
      credentials = DefaultCredentialsProvider.builder().build();
    }

    var builder = BedrockRuntimeClient.builder()
        .region(Region.of(region))
        .credentialsProvider(credentials)
        .overrideConfiguration(override)
        .httpClient(httpClient);

    URI endpointOverride = config.getEndpointOverride();
    if (endpointOverride != null) {
      builder.endpointOverride(endpointOverride);
    }

    return builder.build();
  }
}
