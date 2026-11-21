package com.smartinterview.infrastructure.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * S3 客户端装配：endpoint 覆盖 + path-style 访问（MinIO/RustFS 均要求）。
 * region 使用占位值即可（SDK 必填，S3 兼容实现不校验）。
 */
@Configuration
public class StorageConfig {

  @Bean
  public S3Client s3Client(StorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
        .forcePathStyle(true)
        .build();
  }
}
