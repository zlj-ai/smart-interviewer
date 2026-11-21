package com.smartinterview.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.PromptTemplates;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.infrastructure.storage.ObjectStorageService;
import com.smartinterview.modules.resume.model.ResumeAnalysisResult;
import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.model.ResumeStatus;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;

/**
 * 简历分析服务：对象存储取文件 → Tika 解析 → LLM 结构化分析 → fencing 回写。
 * S3 与 LLM 调用全部在事务外，结果落库收敛为短事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisService {

  private static final int MAX_PARSED_CHARS = 300_000;

  private final ResumeRepository resumeRepository;
  private final ObjectStorageService storageService;
  private final StructuredOutputInvoker outputInvoker;
  private final TransactionalExecutor transactionalExecutor;
  private final LlmClientFactory llmClientFactory;
  private final ObjectMapper analysisMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public void analyze(Long resumeId, int attempt) {
    ResumeEntity resume = resumeRepository.findById(resumeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "简历不存在"));
    if (resume.getStatus() == ResumeStatus.ANALYZED) {
      return;
    }
    resume.setStatus(ResumeStatus.ANALYZING);
    resumeRepository.save(resume);

    byte[] content = storageService.get(resume.getObjectKey());
    String parsedText = parse(content);
    if (parsedText == null || parsedText.isBlank()) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "简历解析结果为空");
    }

    String prompt = PromptTemplates.render("resume-analysis",
        Map.of("resumeText", parsedText));
    ResumeAnalysisResult result = outputInvoker.invoke(prompt,
        ResumeAnalysisResult.class, this::callModel);
    String analysisJson = serialize(result);

    int updated = transactionalExecutor.inTransaction(() ->
        resumeRepository.updateAnalysisFenced(resumeId, attempt, parsedText,
            parsedText.length(), analysisJson));
    if (updated == 0) {
      log.info("分析结果因 fencing 落空被丢弃: resumeId={}, attempt={}", resumeId, attempt);
      return;
    }
    log.info("简历分析完成: resumeId={}, skills={}", resumeId,
        result.skills() == null ? 0 : result.skills().size());
  }

  public void markFailed(Long resumeId, int attempt) {
    int updated = transactionalExecutor.inTransaction(() ->
        resumeRepository.markFailedFenced(resumeId, attempt));
    if (updated == 0) {
      log.info("失败标记因 fencing 落空被丢弃: resumeId={}, attempt={}", resumeId, attempt);
    }
  }

  private String parse(byte[] content) {
    try {
      return new Tika().parseToString(new ByteArrayInputStream(content),
          new Metadata(), MAX_PARSED_CHARS);
    } catch (IOException | TikaException e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "简历解析失败: " + e.getMessage(), e);
    }
  }

  private String serialize(ResumeAnalysisResult result) {
    try {
      return analysisMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.TASK_FAILED, "分析结果序列化失败: " + e.getMessage(), e);
    }
  }

  private String callModel(String prompt) {
    return llmClientFactory.getChatClient().prompt().user(prompt).call().content();
  }
}
