package com.smartinterview.modules.voiceinterview.websocket;

import com.smartinterview.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 握手鉴权：浏览器 EventSource/WebSocket 不便设置请求头，
 * 支持 query 参数 token（?token=xxx）与 Authorization 头两种方式；
 * 鉴权失败拒绝握手，userId 写入 attributes 供 handler 使用。
 */
@Component
@RequiredArgsConstructor
public class VoiceHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                 WebSocketHandler wsHandler, Map<String, Object> attributes) {
    String token = tokenFrom(request);
    if (token == null || !authenticate(token, attributes)) {
      // 拒绝升级并显式返回 401，避免默认的 200 空响应造成语义模糊
      if (response instanceof ServletServerHttpResponse servletResponse) {
        servletResponse.getServletResponse().setStatus(401);
      }
      return false;
    }
    return true;
  }

  private boolean authenticate(String token, Map<String, Object> attributes) {
    try {
      Claims claims = jwtTokenProvider.parse(token);
      if (!JwtTokenProvider.TYPE_ACCESS.equals(claims.get("typ", String.class))) {
        return false;
      }
      attributes.put("userId", Long.valueOf(claims.getSubject()));
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                             WebSocketHandler wsHandler, Exception exception) {
    // 无需处理
  }

  private String tokenFrom(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servletRequest) {
      HttpServletRequest http = servletRequest.getServletRequest();
      String header = http.getHeader("Authorization");
      if (header != null && header.startsWith("Bearer ")) {
        return header.substring(7);
      }
      String query = http.getQueryString();
      if (query != null) {
        for (String pair : query.split("&")) {
          if (pair.startsWith("token=")) {
            return pair.substring("token=".length());
          }
        }
      }
    }
    return null;
  }
}
