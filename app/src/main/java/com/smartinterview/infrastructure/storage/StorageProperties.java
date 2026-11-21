package com.smartinterview.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置（S3 兼容协议：MinIO / RustFS）。
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket
) {
}
