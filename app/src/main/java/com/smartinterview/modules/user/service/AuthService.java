package com.smartinterview.modules.user.service;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.security.JwtTokenProvider;
import com.smartinterview.common.security.RefreshTokenStore;
import com.smartinterview.modules.user.mapper.UserMapper;
import com.smartinterview.modules.user.model.LoginRequest;
import com.smartinterview.modules.user.model.LoginResponse;
import com.smartinterview.modules.user.model.RegisterRequest;
import com.smartinterview.modules.user.model.TokenPairResponse;
import com.smartinterview.modules.user.model.TokenRefreshRequest;
import com.smartinterview.modules.user.model.UserEntity;
import com.smartinterview.modules.user.model.UserResponse;
import com.smartinterview.modules.user.model.UserRole;
import com.smartinterview.modules.user.model.UserStatus;
import com.smartinterview.modules.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：注册、登录、刷新（轮换）、登出（吊销）。
 * 令牌机制：access token 无状态短时效；refresh token 携带 jti 并登记 Redis 白名单，
 * 刷新时先删旧 jti 再发新对（轮换），登出即删白名单，实现 JWT 可吊销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenStore refreshTokenStore;

  @Transactional
  public UserResponse register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.username())) {
      throw new BusinessException(ErrorCode.CONFLICT, "用户名已被占用");
    }
    if (request.email() != null && !request.email().isBlank()
        && userRepository.existsByEmail(request.email())) {
      throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被占用");
    }
    UserEntity user = new UserEntity();
    user.setUsername(request.username());
    user.setEmail(request.email());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.ACTIVE);
    return userMapper.toResponse(userRepository.save(user));
  }

  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    UserEntity user = userRepository.findByUsername(request.username())
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      // 与用户不存在统一提示，避免用户名枚举
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }
    IssuedTokens tokens = issueTokens(user);
    return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
        userMapper.toResponse(user));
  }

  public TokenPairResponse refresh(TokenRefreshRequest request) {
    Claims claims;
    try {
      claims = jwtTokenProvider.parse(request.refreshToken());
    } catch (JwtException e) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
    }
    if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "令牌类型不正确");
    }
    Long userId = Long.valueOf(claims.getSubject());
    String jti = claims.getId();
    if (!refreshTokenStore.exists(userId, jti)) {
      // 白名单未命中：已被轮换或登出吊销
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
    }
    refreshTokenStore.delete(userId, jti);
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));
    IssuedTokens tokens = issueTokens(user);
    return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
  }

  public void logout(TokenRefreshRequest request) {
    try {
      Claims claims = jwtTokenProvider.parse(request.refreshToken());
      if (JwtTokenProvider.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
        refreshTokenStore.delete(Long.valueOf(claims.getSubject()), claims.getId());
      }
    } catch (JwtException | IllegalArgumentException e) {
      // 无效令牌按幂等处理：登出目标状态一致，不向调用方暴露令牌有效性
      log.debug("登出时携带的令牌无效，忽略: {}", e.getMessage());
    }
  }

  private IssuedTokens issueTokens(UserEntity user) {
    String accessToken = jwtTokenProvider.createAccessToken(
        user.getId(), user.getUsername(), user.getRole().name());
    JwtTokenProvider.IssuedRefreshToken refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
    refreshTokenStore.save(user.getId(), refreshToken.jti(), refreshToken.ttl());
    return new IssuedTokens(accessToken, refreshToken.token(), jwtTokenProvider.accessTtl().toSeconds());
  }

  private record IssuedTokens(String accessToken, String refreshToken, Long expiresIn) {
  }
}
