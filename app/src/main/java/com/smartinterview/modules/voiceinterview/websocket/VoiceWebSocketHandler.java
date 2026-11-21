package com.smartinterview.modules.voiceinterview.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.PromptTemplates;
import com.smartinterview.modules.interview.repository.InterviewSessionRepository;
import com.smartinterview.modules.voiceinterview.service.VoiceSessionContext;
import com.smartinterview.modules.voiceinterview.service.VoiceSessionContextManager;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

/**
 * 语音面试 WebSocket 通道：候选人以转写文本（或文字）输入，
 * 服务端基于三级压缩的会话上下文流式返回面试官回应。
 * 消息协议：入站 {"type":"user","text":"..."}；
 * 出站 {"type":"delta"|"done"|"error","content":"..."}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends TextWebSocketHandler {

  private static final ObjectMapper MESSAGE_MAPPER = new ObjectMapper();

  private final VoiceSessionContextManager contextManager;
  private final LlmClientFactory llmClientFactory;
  private final InterviewSessionRepository sessionRepository;

  record ClientMessage(String type, String text) {
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Long sessionId = sessionIdOf(session);
    Long userId = (Long) session.getAttributes().get("userId");
    if (sessionId == null || userId == null) {
      session.close(CloseStatus.NOT_ACCEPTABLE);
      return;
    }
    boolean owned = sessionRepository.findById(sessionId)
        .filter(s -> s.getUserId().equals(userId))
        .isPresent();
    if (!owned) {
      // 防止通过 WebSocket 访问他人会话
      session.close(CloseStatus.NOT_ACCEPTABLE);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    ClientMessage input = MESSAGE_MAPPER.readValue(message.getPayload(), ClientMessage.class);
    if (!"user".equals(input.type()) || input.text() == null || input.text().isBlank()) {
      return;
    }
    Long sessionId = sessionIdOf(session);
    if (sessionId == null) {
      session.close(CloseStatus.NOT_ACCEPTABLE);
      return;
    }
    VoiceSessionContext context = contextManager.contextFor(sessionId);

    String history = context.buildContext(this::summarize);
    context.append("候选人", input.text());
    String prompt = PromptTemplates.render("voice-answer", Map.of(
        "direction", "技术",
        "history", history,
        "question", input.text()));

    streamAnswer(session, sessionId, prompt);
  }

  private void streamAnswer(WebSocketSession session, Long sessionId, String prompt) {
    StringBuilder assistantReply = new StringBuilder();
    AtomicReference<Disposable> subscription = new AtomicReference<>();
    Disposable disposable = llmClientFactory.getChatClient()
        .prompt().user(prompt).stream().content()
        .subscribe(
            chunk -> {
              try {
                assistantReply.append(chunk);
                session.sendMessage(new TextMessage(MESSAGE_MAPPER.writeValueAsString(
                    Map.of("type", "delta", "content", chunk))));
              } catch (IOException e) {
                log.debug("WS 发送失败，客户端可能已断开: {}", e.getMessage());
                Disposable current = subscription.get();
                if (current != null) {
                  current.dispose();
                }
              }
            },
            error -> {
              log.warn("语音面试流式回答失败: sessionId={}, {}", sessionId, error.getMessage());
              sendQuietly(session, "error", String.valueOf(error.getMessage()));
            },
            () -> {
              contextManager.contextFor(sessionId).append("面试官", assistantReply.toString());
              sendQuietly(session, "done", "[DONE]");
            });
    subscription.set(disposable);
  }

  private String summarize(String dialogue) {
    String prompt = PromptTemplates.render("voice-summary", Map.of("dialogue", dialogue));
    return llmClientFactory.getChatClient().prompt().user(prompt).call().content();
  }

  private void sendQuietly(WebSocketSession session, String type, String content) {
    try {
      session.sendMessage(new TextMessage(MESSAGE_MAPPER.writeValueAsString(
          Map.of("type", type, "content", content))));
    } catch (IOException e) {
      log.debug("WS 收尾事件发送失败（客户端可能已断开）: {}", e.getMessage());
    }
  }

  private Long sessionIdOf(WebSocketSession session) {
    Object value = session.getAttributes().get("sessionId");
    return value instanceof Long id ? id : null;
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    // 上下文保留：断线重连可继续对话；会话删除时由业务侧调 clear
  }
}
