package com.smartinterview.modules.voiceinterview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 语音面试的会话上下文：三级压缩控制长会话 token 无界增长。
 * 1) 最近窗口：仅保留最近 N 轮原文；
 * 2) 增量滚动摘要：溢出窗口的轮次由 LLM 增量并入滚动摘要，摘要失败降级为窗口模式（丢弃溢出）；
 * 3) 字符硬预算：拼接结果超预算时从最旧轮次继续丢弃，保证不回退为全量历史。
 */
public class VoiceSessionContext {

  private final int recentWindow;
  private final int charBudget;
  private final List<Round> rounds = new ArrayList<>();
  private String rollingSummary;

  public VoiceSessionContext(int recentWindow, int charBudget) {
    if (recentWindow <= 0 || charBudget <= 0) {
      throw new IllegalArgumentException("窗口与预算必须为正数");
    }
    this.recentWindow = recentWindow;
    this.charBudget = charBudget;
  }

  public record Round(String role, String content) {
  }

  public synchronized void append(String role, String content) {
    rounds.add(new Round(role, content));
  }

  public synchronized void clear() {
    rounds.clear();
    rollingSummary = null;
  }

  /**
   * 构建对话历史文本。summarizer 为 LLM 摘要函数，失败时降级为窗口模式。
   */
  public synchronized String buildContext(Function<String, String> summarizer) {
    compactBySummary(summarizer);
    String context = render();
    // 字符硬预算：从最旧轮次继续丢弃（至少保留最后一轮）
    while (context.length() > charBudget && rounds.size() > 1) {
      rounds.remove(0);
      context = render();
    }
    return context;
  }

  /**
   * 溢出窗口的轮次增量并入滚动摘要（三级之一）。摘要失败降级为窗口模式（溢出轮次移出）。
   * 注意按索引区间清除而非 removeAll：轮次内容可能重复，record equals 会误删窗口内相同内容。
   */
  private void compactBySummary(Function<String, String> summarizer) {
    if (rounds.size() <= recentWindow) {
      return;
    }
    int overflowEnd = rounds.size() - recentWindow;
    StringBuilder merged = new StringBuilder();
    if (rollingSummary != null) {
      merged.append("已有摘要：\n").append(rollingSummary).append("\n\n新增对话：\n");
    }
    for (Round round : rounds.subList(0, overflowEnd)) {
      merged.append(round.role()).append("：").append(round.content()).append('\n');
    }

    try {
      rollingSummary = summarizer.apply(merged.toString());
    } catch (Exception e) {
      // 摘要失败降级：溢出轮次同样移出（窗口模式），不回退全量历史
    } finally {
      rounds.subList(0, overflowEnd).clear();
    }
  }

  private String render() {
    StringBuilder sb = new StringBuilder();
    if (rollingSummary != null && !rollingSummary.isBlank()) {
      sb.append("【此前对话摘要】\n").append(rollingSummary).append("\n\n");
    }
    sb.append("【最近对话】\n");
    rounds.forEach(round -> sb.append(round.role()).append("：").append(round.content()).append('\n'));
    return sb.toString();
  }
}
