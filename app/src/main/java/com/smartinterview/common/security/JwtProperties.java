package com.smartinterview.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置，secret 只从环境变量注入，不得硬编码。
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    Duration accessTtl,
    Duration refreshTtl
) {
}
