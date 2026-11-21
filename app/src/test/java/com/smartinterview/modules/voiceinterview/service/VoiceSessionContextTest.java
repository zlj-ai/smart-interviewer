package com.smartinterview.modules.voiceinterview.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("语音会话上下文：三级压缩")
class VoiceSessionContextTest {

  private static final Function<String, String> FAKE_SUMMARIZER =
      dialogue -> "摘要(" + dialogue.length() + "字符)";

  @Test
  @DisplayName("窗口内轮次不触发摘要，全量原文进入上下文")
  void withinWindowNoSummary() {
    VoiceSessionContext context = new VoiceSessionContext(4, 4000);
    AtomicInteger calls = new AtomicInteger();
    Function<String, String> counting = d -> {
      calls.incrementAndGet();
      return FAKE_SUMMARIZER.apply(d);
    };

    for (int i = 0; i < 3; i++) {
      context.append("候选人", "回答 " + i);
    }
    String history = context.buildContext(counting);

    assertThat(calls.get()).isZero();
    assertThat(history).contains("回答 0").contains("回答 2");
    assertThat(history).doesNotContain("摘要");
  }

  @Test
  @DisplayName("溢出窗口触发增量摘要：溢出轮次并入摘要，窗口轮次保留原文")
  void overflowSummarized() {
    VoiceSessionContext context = new VoiceSessionContext(2, 40000);

    for (int i = 0; i < 5; i++) {
      context.append("候选人", "回答 " + i);
    }
    String history = context.buildContext(FAKE_SUMMARIZER);

    assertThat(history).contains("【此前对话摘要】").contains("摘要(");
    assertThat(history).contains("回答 4");
    assertThat(history).doesNotContain("回答 0");
  }

  @Test
  @DisplayName("增量滚动：第二次溢出时合并已有摘要")
  void incrementalRolling() {
    VoiceSessionContext context = new VoiceSessionContext(2, 40000);
    for (int i = 0; i < 4; i++) {
      context.append("候选人", "回答 " + i);
    }
    context.buildContext(FAKE_SUMMARIZER);
    context.append("候选人", "回答 4");
    context.append("候选人", "回答 5");
    String history = context.buildContext(FAKE_SUMMARIZER);

    // 第二次摘要输入中应包含第一次的摘要结果
    assertThat(history).contains("【此前对话摘要】");
  }

  @Test
  @DisplayName("摘要失败降级为窗口模式：溢出轮次丢弃，上下文仍可用")
  void summaryFailureDegrades() {
    VoiceSessionContext context = new VoiceSessionContext(2, 40000);
    for (int i = 0; i < 5; i++) {
      context.append("候选人", "回答 " + i);
    }
    Function<String, String> broken = d -> {
      throw new RuntimeException("LLM 不可用");
    };

    String history = context.buildContext(broken);

    assertThat(history).doesNotContain("【此前对话摘要】");
    assertThat(history).contains("回答 4");
    assertThat(history).doesNotContain("回答 0");
  }

  @Test
  @DisplayName("字符硬预算：超预算时从最旧轮次继续丢弃，至少保留最后一轮")
  void hardBudgetTruncatesOldest() {
    VoiceSessionContext context = new VoiceSessionContext(6, 60);
    for (int i = 0; i < 6; i++) {
      context.append("候选人", "这是一段比较长的回答内容，用来撑爆字符预算，编号 " + i);
    }
    String history = context.buildContext(FAKE_SUMMARIZER);

    assertThat(history.length()).isLessThanOrEqualTo(60);
    assertThat(history).contains("编号 5");
    assertThat(history).doesNotContain("编号 0");
  }

  @Test
  @DisplayName("清空后上下文重置")
  void clearResets() {
    VoiceSessionContext context = new VoiceSessionContext(2, 4000);
    context.append("候选人", "内容");
    context.clear();

    String history = context.buildContext(FAKE_SUMMARIZER);
    assertThat(history).doesNotContain("内容");
  }

  @Test
  @DisplayName("同内容重复轮次不会被摘要清除误删（按索引区间清理）")
  void duplicateRoundsSurviveWindow() {
    VoiceSessionContext context = new VoiceSessionContext(2, 40000);
    for (int i = 0; i < 5; i++) {
      context.append("候选人", "重复回答");
    }
    String history = context.buildContext(FAKE_SUMMARIZER);

    // 窗口内保留 2 轮相同内容（record equals 误删会只剩 1 轮）
    assertThat(history.split("重复回答", -1).length - 1).isEqualTo(2);
  }
}
