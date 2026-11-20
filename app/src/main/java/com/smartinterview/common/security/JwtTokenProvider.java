package com.smartinterview.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。access token 无状态短时效；refresh token 携带 jti，
 * 配合 {@link RefreshTokenStore} 白名单实现可吊销与轮换。
 */
@Component
public class JwtTokenProvider {

  private static final String CLAIM_TYP = "typ";
  public static final String TYPE_ACCESS = "access";
  public static final String TYPE_REFRESH = "refresh";

  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtTokenProvider(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.accessTtl = properties.accessTtl();
    this.refreshTtl = properties.refreshTtl();
  }

  public Duration accessTtl() {
    return accessTtl;
  }

  public String createAccessToken(Long userId, String username, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("username", username)
        .claim("role", role)
        .claim(CLAIM_TYP, TYPE_ACCESS)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTtl)))
        .signWith(key)
        .compact();
  }

  public IssuedRefreshToken createRefreshToken(Long userId) {
    String jti = UUID.randomUUID().toString();
    Instant now = Instant.now();
    String token = Jwts.builder()
        .subject(String.valueOf(userId))
        .id(jti)
        .claim(CLAIM_TYP, TYPE_REFRESH)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(refreshTtl)))
        .signWith(key)
        .compact();
    return new IssuedRefreshToken(token, jti, refreshTtl);
  }

  /**
   * 解析并校验签名与有效期，无效或过期抛出 JwtException 子类。
   */
  public Claims parse(String token) throws JwtException {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /**
   * 签发的 refresh token：token 本体 + jti（用于白名单登记与轮换）。
   */
  public record IssuedRefreshToken(String token, String jti, Duration ttl) {
  }
}
