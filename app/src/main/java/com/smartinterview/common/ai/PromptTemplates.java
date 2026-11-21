package com.smartinterview.common.ai;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载与渲染：模板文件统一放在 resources/prompts/{name}.st，
 * StringTemplate 语法（{var}），模板文本按名称缓存。
 */
public final class PromptTemplates {

  private static final String BASE_PATH = "prompts/";
  private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();
  private static final TemplateRenderer RENDERER = StTemplateRenderer.builder().build();

  private PromptTemplates() {
  }

  public static String render(String name, Map<String, Object> variables) {
    String template = TEMPLATE_CACHE.computeIfAbsent(name, PromptTemplates::load);
    try {
      return RENDERER.apply(template, variables);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "Prompt 模板渲染失败: " + name + " - " + e.getMessage());
    }
  }

  private static String load(String name) {
    String location = BASE_PATH + name + ".st";
    try (InputStream in = PromptTemplates.class.getClassLoader().getResourceAsStream(location)) {
      if (in == null) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "Prompt 模板不存在: " + location);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "Prompt 模板读取失败: " + location, e);
    }
  }
}
