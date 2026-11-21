package com.smartinterview.modules.voiceinterview.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册：/ws/voice/{sessionId}，握手阶段完成 JWT 鉴权。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VoiceWebSocketConfig implements WebSocketConfigurer {

  private final VoiceWebSocketHandler voiceWebSocketHandler;
  private final VoiceHandshakeInterceptor handshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(voiceWebSocketHandler, "/ws/voice/{sessionId}")
        .addInterceptors(handshakeInterceptor)
        .setAllowedOrigins("http://localhost:5173");
  }
}
