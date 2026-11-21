package com.smartinterview.modules.voiceinterview.service;

import com.smartinterview.modules.voiceinterview.config.VoiceProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 语音会话上下文管理器：按面试会话 id 维护独立上下文（单实例内存态）。
 */
@Component
public class VoiceSessionContextManager {

  private final VoiceProperties properties;
  private final Map<Long, VoiceSessionContext> contexts = new ConcurrentHashMap<>();

  public VoiceSessionContextManager(VoiceProperties properties) {
    this.properties = properties;
  }

  public VoiceSessionContext contextFor(Long interviewSessionId) {
    return contexts.computeIfAbsent(interviewSessionId,
        id -> new VoiceSessionContext(properties.recentWindow(), properties.charBudget()));
  }

  public void clear(Long interviewSessionId) {
    contexts.remove(interviewSessionId);
  }
}
