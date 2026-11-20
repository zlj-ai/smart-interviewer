package com.smartinterview.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 Authorization: Bearer 头解析 access token 并还原认证上下文。
 * 无效令牌只清除上下文（视为匿名），由安全入口点统一返回 401。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      try {
        Claims claims = jwtTokenProvider.parse(header.substring(7));
        if (JwtTokenProvider.TYPE_ACCESS.equals(claims.get("typ", String.class))) {
          CurrentUser currentUser = new CurrentUser(
              Long.valueOf(claims.getSubject()),
              claims.get("username", String.class),
              claims.get("role", String.class));
          UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
              currentUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role())));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (JwtException | IllegalArgumentException e) {
        log.debug("无效的 access token: {}", e.getMessage());
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }
}
