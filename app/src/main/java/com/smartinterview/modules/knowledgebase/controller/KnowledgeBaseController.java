package com.smartinterview.modules.knowledgebase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.result.PageResponse;
import com.smartinterview.common.result.Result;
import com.smartinterview.common.security.SecurityUtils;
import com.smartinterview.modules.knowledgebase.model.AskRequest;
import com.smartinterview.modules.knowledgebase.model.CreateKbRequest;
import com.smartinterview.modules.knowledgebase.model.KbDocumentResponse;
import com.smartinterview.modules.knowledgebase.model.KbResponse;
import com.smartinterview.modules.knowledgebase.service.DocumentService;
import com.smartinterview.modules.knowledgebase.service.KnowledgeBaseService;
import com.smartinterview.modules.knowledgebase.service.RagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@Slf4j
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@Validated
public class KnowledgeBaseController {

  private static final ObjectMapper SSE_OBJECT_MAPPER = new ObjectMapper();
  private static final long SSE_TIMEOUT_MILLIS = 120_000L;

  private final KnowledgeBaseService kbService;
  private final DocumentService documentService;
  private final RagService ragService;
  private final LlmClientFactory llmClientFactory;

  @PostMapping
  public Result<KbResponse> create(@Valid @RequestBody CreateKbRequest request) {
    return Result.ok(kbService.create(request, SecurityUtils.requireCurrentUserId()));
  }

  @GetMapping
  public Result<PageResponse<KbResponse>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return Result.ok(kbService.list(SecurityUtils.requireCurrentUserId(), pageable));
  }

  @GetMapping("/{id}")
  public Result<KbResponse> detail(@PathVariable Long id) {
    return Result.ok(kbService.get(id, SecurityUtils.requireCurrentUserId()));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    kbService.delete(id, SecurityUtils.requireCurrentUserId());
    return Result.ok();
  }

  @PostMapping("/{id}/documents")
  public Result<KbDocumentResponse> upload(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file) {
    return Result.ok(documentService.upload(id, SecurityUtils.requireCurrentUserId(), file));
  }

  @GetMapping("/{id}/documents")
  public Result<List<KbDocumentResponse>> documents(@PathVariable Long id) {
    return Result.ok(kbService.listDocuments(id, SecurityUtils.requireCurrentUserId()));
  }

  @DeleteMapping("/{id}/documents/{docId}")
  public Result<Void> deleteDocument(@PathVariable Long id, @PathVariable Long docId) {
    kbService.deleteDocument(id, docId, SecurityUtils.requireCurrentUserId());
    return Result.ok();
  }

  /**
   * RAG 问答（SSE）：事件协议 sources（来源清单，先行下发）→ delta → done/error。
   * 归属校验与检索在返回 emitter 前同步完成。
   */
  @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter ask(@Valid @RequestBody AskRequest request) throws IOException {
    Long userId = SecurityUtils.requireCurrentUserId();
    RagService.RagContext context = ragService.prepare(request, userId);

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    emitter.send(SseEmitter.event().name("sources")
        .data(SSE_OBJECT_MAPPER.writeValueAsString(context.sources()), MediaType.APPLICATION_JSON));

    AtomicReference<Disposable> subscription = new AtomicReference<>();
    Disposable disposable = llmClientFactory.getChatClient()
        .prompt().user(context.prompt()).stream().content()
        .subscribe(
            chunk -> {
              try {
                emitter.send(SseEmitter.event().name("delta").data(chunk, MediaType.TEXT_PLAIN));
              } catch (IOException e) {
                log.debug("SSE 发送失败，客户端可能已断开: {}", e.getMessage());
                Disposable current = subscription.get();
                if (current != null) {
                  current.dispose();
                }
                emitter.complete();
              }
            },
            error -> {
              log.warn("RAG 回答生成失败: question={}, {}", request.question(), error.getMessage());
              sendEventQuietly(emitter, "error", String.valueOf(error.getMessage()));
              emitter.complete();
            },
            () -> {
              sendEventQuietly(emitter, "done", "[DONE]");
              emitter.complete();
            });
    subscription.set(disposable);
    emitter.onTimeout(() -> {
      disposable.dispose();
      emitter.complete();
    });
    return emitter;
  }

  private void sendEventQuietly(SseEmitter emitter, String event, String data) {
    try {
      emitter.send(SseEmitter.event().name(event).data(data, MediaType.TEXT_PLAIN));
    } catch (IOException e) {
      log.debug("SSE 收尾事件发送失败（客户端可能已断开）: {}", e.getMessage());
    }
  }
}
