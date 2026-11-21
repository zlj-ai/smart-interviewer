package com.smartinterview.modules.knowledgebase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.PromptTemplates;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.modules.knowledgebase.model.AskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答准备服务：查询改写 → 双路召回融合 → 上下文与来源构建。
 * - 改写是增强而非依赖：LLM 改写失败降级用原始查询，原始查询一路仍可召回；
 * - 双路召回：原始查询与改写查询分别召回，按文档 id 去重保留高分项；
 * - 上下文片段带编号，回答可引用 [来源N]，来源清单随 SSE sources 事件下发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

  private static final int TOP_K = 5;
  private static final TypeReference<RewrittenQuery> REWRITE_TYPE = new TypeReference<>() {
  };

  private final VectorStore vectorStore;
  private final StructuredOutputInvoker outputInvoker;
  private final LlmClientFactory llmClientFactory;

  public record RewrittenQuery(String query) {
  }

  public record SourceRef(int index, Long docId, String filename, Double score) {
  }

  public record RagContext(String prompt, List<SourceRef> sources) {
  }

  /**
   * 构建流式回答所需的 Prompt 与来源清单。
   */
  public RagContext prepare(AskRequest request, Long userId) {
    String rewritten = rewriteQuery(request.question());

    Filter.Expression filter = buildFilter(request.kbId(), userId);
    List<Document> merged = dualRecall(request.question(), rewritten, filter);

    if (merged.isEmpty()) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "知识库中未检索到与问题相关的内容");
    }

    List<SourceRef> sources = new ArrayList<>();
    StringBuilder context = new StringBuilder();
    for (int i = 0; i < merged.size(); i++) {
      Document doc = merged.get(i);
      int index = i + 1;
      context.append("[来源").append(index).append("] ").append(doc.getText()).append("\n\n");
      Long docId = toLong(doc.getMetadata().get(VectorizeService.METADATA_DOC_ID));
      sources.add(new SourceRef(index, docId,
          String.valueOf(doc.getMetadata().getOrDefault(VectorizeService.METADATA_FILENAME, "unknown")),
          doc.getScore()));
    }

    String prompt = PromptTemplates.render("rag-answer", Map.of(
        "context", context.toString(),
        "question", request.question()));
    return new RagContext(prompt, sources);
  }

  /**
   * 查询改写：失败降级为原始查询，不阻断问答。
   */
  private String rewriteQuery(String question) {
    try {
      String prompt = PromptTemplates.render("query-rewrite", Map.of("question", question));
      return outputInvoker.invoke(prompt, REWRITE_TYPE, this::callModel).query();
    } catch (BusinessException e) {
      log.warn("查询改写失败，降级使用原始查询: {}", e.getMessage());
      return question;
    }
  }

  /**
   * 双路召回：原始查询 + 改写查询各召回 TOP_K，按文档 id 去重保留高分项后截断。
   */
  private List<Document> dualRecall(String original, String rewritten, Filter.Expression filter) {
    List<Document> fromOriginal = vectorStore.similaritySearch(SearchRequest.builder()
        .query(original).topK(TOP_K).filterExpression(filter).build());
    List<Document> fromRewritten = rewritten.equals(original) ? List.of()
        : vectorStore.similaritySearch(SearchRequest.builder()
            .query(rewritten).topK(TOP_K).filterExpression(filter).build());

    Map<String, Document> merged = new LinkedHashMap<>();
    for (Document doc : fromOriginal) {
      merged.putIfAbsent(doc.getId(), doc);
    }
    for (Document doc : fromRewritten) {
      merged.putIfAbsent(doc.getId(), doc);
    }
    List<Document> result = new ArrayList<>(merged.values());
    return result.size() > TOP_K ? result.subList(0, TOP_K) : result;
  }

  private Filter.Expression buildFilter(Long kbId, Long userId) {
    FilterExpressionBuilder builder = new FilterExpressionBuilder();
    FilterExpressionBuilder.Op userFilter = builder.eq(
        VectorizeService.METADATA_USER_ID, String.valueOf(userId));
    if (kbId == null) {
      return userFilter.build();
    }
    return builder.and(userFilter,
        builder.eq(VectorizeService.METADATA_KB_ID, String.valueOf(kbId))).build();
  }

  private Long toLong(Object value) {
    try {
      return value == null ? null : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private String callModel(String prompt) {
    return llmClientFactory.getChatClient().prompt().user(prompt).call().content();
  }
}
