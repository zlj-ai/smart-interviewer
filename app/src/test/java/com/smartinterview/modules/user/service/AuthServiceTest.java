package com.smartinterview.modules.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.security.JwtTokenProvider;
import com.smartinterview.common.security.RefreshTokenStore;
import com.smartinterview.modules.user.mapper.UserMapperImpl;
import com.smartinterview.modules.user.model.LoginRequest;
import com.smartinterview.modules.user.model.RegisterRequest;
import com.smartinterview.modules.user.model.TokenPairResponse;
import com.smartinterview.modules.user.model.TokenRefreshRequest;
import com.smartinterview.modules.user.model.UserEntity;
import com.smartinterview.modules.user.model.UserRole;
import com.smartinterview.modules.user.model.UserStatus;
import com.smartinterview.modules.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务")
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private JwtTokenProvider jwtTokenProvider;
  @Mock
  private RefreshTokenStore refreshTokenStore;
  @Mock
  private Claims claims;

  private final UserMapperImpl userMapper = new UserMapperImpl();
  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userRepository, userMapper, passwordEncoder,
        jwtTokenProvider, refreshTokenStore);
  }

  private UserEntity activeUser() {
    UserEntity user = new UserEntity();
    user.setId(42L);
    user.setUsername("alice");
    user.setEmail("alice@example.com");
    user.setPasswordHash("encoded-hash");
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.ACTIVE);
    return user;
  }

  @Nested
  @DisplayName("注册")
  class Register {

    @Test
    @DisplayName("用户名已占用时报冲突且不落库")
    void duplicateUsername() {
      when(userRepository.existsByUsername("alice")).thenReturn(true);

      assertThatThrownBy(() -> authService.register(
          new RegisterRequest("alice", "password123", null)))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).contains("用户名已被占用"));
      verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("邮箱已占用时报冲突")
    void duplicateEmail() {
      when(userRepository.existsByUsername("alice")).thenReturn(false);
      when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

      assertThatThrownBy(() -> authService.register(
          new RegisterRequest("alice", "password123", "alice@example.com")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("注册成功：密码编码后落库并返回用户信息")
    void success() {
      when(userRepository.existsByUsername("alice")).thenReturn(false);
      when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
      when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
        UserEntity user = invocation.getArgument(0);
        user.setId(42L);
        return user;
      });

      var response = authService.register(
          new RegisterRequest("alice", "password123", "alice@example.com"));

      assertThat(response.id()).isEqualTo(42L);
      assertThat(response.username()).isEqualTo("alice");
      assertThat(response.role()).isEqualTo("USER");
      verify(userRepository).save(any(UserEntity.class));
    }
  }

  @Nested
  @DisplayName("登录")
  class Login {

    @Test
    @DisplayName("用户不存在时统一提示为用户名或密码错误（防枚举）")
    void unknownUser() {
      when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "password123")))
          .isInstanceOfSatisfying(BusinessException.class, e ->
              assertThat(e.getMessage()).isEqualTo("用户名或密码错误"));
    }

    @Test
    @DisplayName("密码错误时报未授权")
    void wrongPassword() {
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
      when(passwordEncoder.matches("wrong-pass", "encoded-hash")).thenReturn(false);

      assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-pass")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("账号被禁用时报禁止访问")
    void disabledUser() {
      UserEntity user = activeUser();
      user.setStatus(UserStatus.DISABLED);
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "password123")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("登录成功：签发双 token 并登记 refresh 白名单")
    void success() {
      when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
      when(passwordEncoder.matches("password123", "encoded-hash")).thenReturn(true);
      when(jwtTokenProvider.createAccessToken(42L, "alice", "USER")).thenReturn("access-token");
      when(jwtTokenProvider.createRefreshToken(42L))
          .thenReturn(new JwtTokenProvider.IssuedRefreshToken("refresh-token", "jti-1", Duration.ofDays(7)));
      when(jwtTokenProvider.accessTtl()).thenReturn(Duration.ofHours(2));

      var response = authService.login(new LoginRequest("alice", "password123"));

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.expiresIn()).isEqualTo(7200L);
      assertThat(response.user().username()).isEqualTo("alice");
      verify(refreshTokenStore).save(42L, "jti-1", Duration.ofDays(7));
    }
  }

  @Nested
  @DisplayName("刷新")
  class Refresh {

    @Test
    @DisplayName("签名无效时报未授权且不轮换")
    void invalidSignature() {
      when(jwtTokenProvider.parse("bad-token")).thenThrow(new JwtException("broken"));

      assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest("bad-token")))
          .isInstanceOf(BusinessException.class);
      verify(refreshTokenStore, never()).delete(any(), anyString());
    }

    @Test
    @DisplayName("access token 不能用于刷新")
    void wrongType() {
      when(jwtTokenProvider.parse("access-as-refresh")).thenReturn(claims);
      when(claims.get("typ", String.class)).thenReturn("access");

      assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest("access-as-refresh")))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("白名单未命中（已轮换或登出）时拒绝")
    void revoked() {
      when(jwtTokenProvider.parse("old-refresh")).thenReturn(claims);
      when(claims.get("typ", String.class)).thenReturn("refresh");
      when(claims.getSubject()).thenReturn("42");
      when(claims.getId()).thenReturn("old-jti");
      when(refreshTokenStore.exists(42L, "old-jti")).thenReturn(false);

      assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest("old-refresh")))
          .isInstanceOf(BusinessException.class);
      verify(refreshTokenStore, never()).delete(any(), anyString());
    }

    @Test
    @DisplayName("刷新成功：旧 jti 作废、新 jti 登记、返回新 token 对")
    void successWithRotation() {
      when(jwtTokenProvider.parse("old-refresh")).thenReturn(claims);
      when(claims.get("typ", String.class)).thenReturn("refresh");
      when(claims.getSubject()).thenReturn("42");
      when(claims.getId()).thenReturn("old-jti");
      when(refreshTokenStore.exists(42L, "old-jti")).thenReturn(true);
      when(userRepository.findById(42L)).thenReturn(Optional.of(activeUser()));
      when(jwtTokenProvider.createAccessToken(42L, "alice", "USER")).thenReturn("new-access");
      when(jwtTokenProvider.createRefreshToken(42L))
          .thenReturn(new JwtTokenProvider.IssuedRefreshToken("new-refresh", "new-jti", Duration.ofDays(7)));
      when(jwtTokenProvider.accessTtl()).thenReturn(Duration.ofHours(2));

      TokenPairResponse response = authService.refresh(new TokenRefreshRequest("old-refresh"));

      assertThat(response.accessToken()).isEqualTo("new-access");
      assertThat(response.refreshToken()).isEqualTo("new-refresh");
      verify(refreshTokenStore).delete(42L, "old-jti");
      verify(refreshTokenStore).save(42L, "new-jti", Duration.ofDays(7));
    }
  }

  @Nested
  @DisplayName("登出")
  class Logout {

    @Test
    @DisplayName("有效 refresh token：吊销对应 jti")
    void revoke() {
      when(jwtTokenProvider.parse("refresh-token")).thenReturn(claims);
      when(claims.get("typ", String.class)).thenReturn("refresh");
      when(claims.getSubject()).thenReturn("42");
      when(claims.getId()).thenReturn("jti-1");

      authService.logout(new TokenRefreshRequest("refresh-token"));

      verify(refreshTokenStore).delete(42L, "jti-1");
    }

    @Test
    @DisplayName("无效令牌：幂等处理，不抛异常也不吊销")
    void invalidTokenIsIdempotent() {
      when(jwtTokenProvider.parse("broken")).thenThrow(new JwtException("broken"));

      authService.logout(new TokenRefreshRequest("broken"));

      verify(refreshTokenStore, never()).delete(any(), anyString());
    }
  }
}
