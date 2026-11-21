package com.smartinterview.common.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 面向中文的确定性文本分块器。
 * 规则：按行聚合段落，段内超长时滑窗硬切（保留 overlap），
 * 段落边界优先作为切分点；全程无随机性，重试重切结果一致。
 */
public final class TextChunker {

  private TextChunker() {
  }

  public static List<String> chunk(String text, int maxChars, int overlap) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    if (maxChars <= 0) {
      throw new IllegalArgumentException("maxChars 必须为正数");
    }
    int safeOverlap = Math.min(Math.max(overlap, 0), maxChars / 2);

    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
      String trimmed = line.strip();
      // 空行是软边界：内容为空不产生块，短段落仍可合并（检索质量优先）
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.length() > maxChars) {
        flush(chunks, current);
        chunks.addAll(splitLongLine(trimmed, maxChars, safeOverlap));
        continue;
      }
      if (current.length() + trimmed.length() + 1 > maxChars) {
        flush(chunks, current);
      }
      if (current.length() > 0) {
        current.append('\n');
      }
      current.append(trimmed);
    }
    flush(chunks, current);
    return chunks;
  }

  /**
   * 滑窗硬切超长行：每个窗口 maxChars，相邻窗口重叠 overlap。
   */
  private static List<String> splitLongLine(String line, int maxChars, int overlap) {
    List<String> pieces = new ArrayList<>();
    int step = maxChars - overlap;
    int start = 0;
    while (start < line.length()) {
      int end = Math.min(start + maxChars, line.length());
      pieces.add(line.substring(start, end));
      if (end == line.length()) {
        break;
      }
      start += step;
    }
    return pieces;
  }

  private static void flush(List<String> chunks, StringBuilder current) {
    String content = current.toString().strip();
    if (!content.isEmpty()) {
      chunks.add(content);
    }
    current.setLength(0);
  }
}
