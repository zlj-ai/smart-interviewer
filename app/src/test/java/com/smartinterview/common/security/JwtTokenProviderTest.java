package com.smartinterview.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JWT 签发与解析")
class JwtTokenProviderTest {

  private static final String SECRET =
      "unit-test-secret-0123456789abcdef-unit-test-secret-0123456789abcdef";

  private JwtTokenProvider provider;

  @BeforeEach
  void setUp() {
    provider = new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofHours(2), Duration.ofDays(7)));
  }

  @Nested
  @DisplayName("access token")
  class AccessToken {

    @Test
    @DisplayName("生成后可解析且 claims 一致")
    void roundTrip() {
      String token = provider.createAccessToken(42L, "alice", "USER");

      Claims claims = provider.parse(token);
      assertThat(claims.getSubject()).isEqualTo("42");
      assertThat(claims.get("username", String.class)).isEqualTo("alice");
      assertThat(claims.get("role", String.class)).isEqualTo("USER");
      assertThat(claims.get("typ", String.class)).isEqualTo("access");
    }

    @Test
    @DisplayName("过期后解析抛出过期异常")
    void expired() throws InterruptedException {
      JwtTokenProvider shortLived = new JwtTokenProvider(
          new JwtProperties(SECRET, Duration.ofMillis(1), Duration.ofDays(7)));
      String token = shortLived.createAccessToken(1L, "bob", "USER");
      Thread.sleep(50);

      assertThatThrownBy(() -> shortLived.parse(token))
          .isInstanceOf(ExpiredJwtException.class);
    }
  }

  @Nested
  @DisplayName("refresh token")
  class RefreshToken {

    @Test
    @DisplayName("携带 jti 且类型为 refresh，ttl 透传给白名单登记")
    void hasJti() {
      JwtTokenProvider.IssuedRefreshToken issued = provider.createRefreshToken(7L);

      Claims claims = provider.parse(issued.token());
      assertThat(claims.getId()).isEqualTo(issued.jti());
      assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
      assertThat(issued.ttl()).isEqualTo(Duration.ofDays(7));
    }
  }

  @Test
  @DisplayName("令牌被篡改时解析失败")
  void tampered() {
    String token = provider.createAccessToken(1L, "alice", "USER") + "x";

    assertThatThrownBy(() -> provider.parse(token))
        .isInstanceOf(JwtException.class);
  }
}
