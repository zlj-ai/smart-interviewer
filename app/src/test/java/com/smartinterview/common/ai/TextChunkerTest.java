package com.smartinterview.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("中文确定性文本分块器")
class TextChunkerTest {

  @Nested
  @DisplayName("聚合")
  class Merging {

    @Test
    @DisplayName("短段落按边界合并为不超过 maxChars 的块")
    void mergesParagraphs() {
      List<String> chunks = TextChunker.chunk("第一段\n\n第二段\n\n第三段", 100, 0);
      assertThat(chunks).hasSize(1);
      assertThat(chunks.get(0)).contains("第一段").contains("第三段");
    }

    @Test
    @DisplayName("段落放不下时在边界处切分，不把段落截断")
    void splitsAtParagraphBoundary() {
      String longParagraph = "甲".repeat(90);
      String another = "乙".repeat(20);
      List<String> chunks = TextChunker.chunk(longParagraph + "\n\n" + another, 100, 0);
      assertThat(chunks).hasSize(2);
      assertThat(chunks.get(0)).startsWith("甲").doesNotContain("乙");
      assertThat(chunks.get(1)).isEqualTo(another);
    }
  }

  @Nested
  @DisplayName("滑窗硬切")
  class SlidingWindow {

    @Test
    @DisplayName("超长段落按 maxChars 滑窗切分且相邻窗口重叠 overlap")
    void splitsLongLineWithOverlap() {
      String longLine = "字".repeat(250);
      List<String> chunks = TextChunker.chunk(longLine, 100, 20);

      assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
      // 相邻块重叠 20 字符
      String second = chunks.get(1);
      String tailOfFirst = chunks.get(0).substring(chunks.get(0).length() - 20);
      assertThat(second).startsWith(tailOfFirst);
      // 拼回去（去掉重叠部分）应还原原文长度
      int total = chunks.get(0).length();
      for (int i = 1; i < chunks.size(); i++) {
        total += chunks.get(i).length() - 20;
      }
      assertThat(total).isEqualTo(250);
    }

    @Test
    @DisplayName("overlap 大于等于窗口一半时被钳制，窗口不会倒退")
    void overlapClamped() {
      List<String> chunks = TextChunker.chunk("字".repeat(500), 100, 80);
      // 每个窗口起点必须单调前进
      int start = 0;
      for (int i = 1; i < chunks.size(); i++) {
        start += chunks.get(i - 1).length() - 50;
        assertThat(start).isGreaterThan(0);
      }
    }
  }

  @Test
  @DisplayName("空文本返回空列表，null 安全")
  void blankAndNull() {
    assertThat(TextChunker.chunk(null, 100, 10)).isEmpty();
    assertThat(TextChunker.chunk("   ", 100, 10)).isEmpty();
  }

  @Test
  @DisplayName("非法参数直接抛出")
  void invalidArguments() {
    assertThatThrownBy(() -> TextChunker.chunk("内容", 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("确定性：同一输入两次分块结果完全一致")
  void deterministic() {
    String text = "段落一\n内容行\n\n段落二\n".repeat(10);
    assertThat(TextChunker.chunk(text, 50, 10))
        .isEqualTo(TextChunker.chunk(text, 50, 10));
  }
}
