package com.smartinterview.modules.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.ai.StructuredOutputInvoker;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.modules.knowledgebase.model.AskRequest;
import com.smartinterview.modules.knowledgebase.service.RagService.RagContext;
import com.smartinterview.modules.knowledgebase.service.RagService.RewrittenQuery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.fasterxml.jackson.core.type.TypeReference;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
@DisplayName("RAG 问答准备服务（查询改写 + 双路召回）")
class RagServiceTest {

  private static final Long USER_ID = 1L;

  @Mock
  private VectorStore vectorStore;
  @Mock
  private StructuredOutputInvoker outputInvoker;
  @Mock
  private LlmClientFactory llmClientFactory;

  private RagService service;

  @BeforeEach
  void setUp() {
    service = new RagService(vectorStore, outputInvoker, llmClientFactory);
  }

  private Document doc(String id, String text, String docId, String filename, double score) {
    return Document.builder()
        .id(id)
        .text(text)
        .metadata("docId", docId)
        .metadata("filename", filename)
        .metadata("userId", "1")
        .score(score)
        .build();
  }

  @Test
  @DisplayName("双路召回：两路结果按文档 id 去重合并，来源编号连续且保留高分项")
  void dualRecallMergeAndDedupe() {
    when(outputInvoker.invoke(anyString(), ArgumentMatchers.<TypeReference<RewrittenQuery>>any(), any()))
        .thenReturn(new RewrittenQuery("volatile 可见性原理"));
    Document a = doc("d1", "文本一", "1", "并发.txt", 0.9);
    Document b = doc("d2", "文本二", "2", "并发.txt", 0.8);
    Document c = doc("d3", "文本三", "3", "JMM.txt", 0.7);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
      SearchRequest request = invocation.getArgument(0);
      if ("volatile 可见性原理".equals(request.getQuery())) {
        return List.of(b, c);
      }
      return List.of(a, b);
    });

    RagContext context = service.prepare(new AskRequest("volatile 有什么用", null), USER_ID);

    assertThat(context.sources()).extracting("index").containsExactly(1, 2, 3);
    assertThat(context.sources()).extracting("docId").containsExactly(1L, 2L, 3L);
    // 去重保留原始查询一路的高分项（0.8 而非改写路的 0.7）
    assertThat(context.sources().get(1).score()).isEqualTo(0.8);
    assertThat(context.prompt()).contains("[来源1]").contains("[来源3]");
    assertThat(context.prompt()).contains("volatile 有什么用");
  }

  @Test
  @DisplayName("查询改写失败时降级用原始查询，只召回一路且结果正常")
  void rewriteFailureDegrades() {
    when(outputInvoker.invoke(anyString(), ArgumentMatchers.<TypeReference<RewrittenQuery>>any(), any()))
        .thenThrow(new BusinessException(ErrorCode.TASK_FAILED, "改写失败"));
    Document a = doc("d1", "文本一", "1", "并发.txt", 0.9);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(a));

    RagContext context = service.prepare(new AskRequest("原始问题", null), USER_ID);

    assertThat(context.sources()).hasSize(1);
    assertThat(context.prompt()).contains("[来源1]");
    verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
  }

  @Test
  @DisplayName("两路均无召回时提示未检索到相关知识")
  void emptyRecall() {
    when(outputInvoker.invoke(anyString(), ArgumentMatchers.<TypeReference<RewrittenQuery>>any(), any()))
        .thenReturn(new RewrittenQuery("改写后"));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    assertThatThrownBy(() -> service.prepare(new AskRequest("无人知晓的问题", null), USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, e ->
            assertThat(e.getMessage()).contains("未检索到"));
  }

  @Test
  @DisplayName("指定 kbId 时过滤表达式包含知识库条件，未指定时仅按用户过滤")
  void filterScope() {
    when(outputInvoker.invoke(anyString(), ArgumentMatchers.<TypeReference<RewrittenQuery>>any(), any()))
        .thenReturn(new RewrittenQuery("改写后"));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(doc("d1", "文本一", "1", "并发.txt", 0.9)));

    service.prepare(new AskRequest("问题一", 5L), USER_ID);
    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, times(2)).similaritySearch(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(request ->
        assertThat(request.getFilterExpression()).isNotNull());

    service.prepare(new AskRequest("问题二", null), USER_ID);
    verify(vectorStore, times(4)).similaritySearch(captor.capture());
    assertThat(captor.getAllValues().subList(2, 4)).allSatisfy(request ->
        assertThat(request.getFilterExpression()).isNotNull());
  }
}
