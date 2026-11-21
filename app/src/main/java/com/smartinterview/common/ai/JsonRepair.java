package com.smartinterview.common.ai;

/**
 * LLM 返回 JSON 的本地修复工具。
 * 状态机逐字符扫描：追踪字符串与转义状态、括号深度，做到字符串内的
 * 花括号、逗号不干扰提取；只做结构修复，不尝试猜测语义。
 */
public final class JsonRepair {

  private JsonRepair() {
  }

  /**
   * 从 LLM 原始输出中提取首个完整 JSON（对象或数组）并修复字符串外的尾随逗号。
   * 无法定位 JSON 时原样返回，交由上层解析并重试。
   */
  public static String extract(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    String cleaned = stripCodeFence(raw.trim());
    int start = indexOfStart(cleaned);
    if (start < 0) {
      return cleaned;
    }
    int end = matchingClose(cleaned, start);
    if (end < 0) {
      return cleaned;
    }
    return stripTrailingCommas(cleaned.substring(start, end + 1));
  }

  /**
   * 剥离 Markdown 代码围栏（```json ... ```）。
   */
  private static String stripCodeFence(String text) {
    if (!text.startsWith("```")) {
      return text;
    }
    int firstLineEnd = text.indexOf('\n');
    if (firstLineEnd < 0) {
      return text;
    }
    int close = text.lastIndexOf("```");
    if (close <= firstLineEnd) {
      return text;
    }
    return text.substring(firstLineEnd + 1, close).trim();
  }

  private static int indexOfStart(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{' || c == '[') {
        return i;
      }
      // 非空白前导字符且不可能是前缀说明时仍继续扫，容忍任意说明文字
    }
    return -1;
  }

  /**
   * 从 start 位置找到与首括号匹配的闭括号下标；字符串内字符与转义不参与计数。
   */
  private static int matchingClose(String text, int start) {
    char open = text.charAt(start);
    char close = open == '{' ? '}' : ']';
    boolean inString = false;
    boolean escaped = false;
    int depth = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (inString) {
        if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{' || c == '[') {
        depth++;
      } else if (c == '}' || c == ']') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * 删除字符串外的尾随逗号（对象与数组末尾），仅结构层处理。
   */
  private static String stripTrailingCommas(String json) {
    StringBuilder sb = new StringBuilder(json.length());
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < json.length(); i++) {
      char c = json.charAt(i);
      if (escaped) {
        escaped = false;
        sb.append(c);
        continue;
      }
      if (inString) {
        if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        sb.append(c);
        continue;
      }
      if (c == '"') {
        inString = true;
        sb.append(c);
        continue;
      }
      if (c == ',') {
        int j = i + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
          j++;
        }
        if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
          continue;
        }
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
