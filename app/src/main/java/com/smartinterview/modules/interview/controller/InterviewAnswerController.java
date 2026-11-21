package com.smartinterview.modules.interview.controller;

import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.common.result.Result;
import com.smartinterview.common.security.SecurityUtils;
import com.smartinterview.modules.interview.model.AnswerResponse;
import com.smartinterview.modules.interview.model.SubmitAnswerRequest;
import com.smartinterview.modules.interview.service.AnswerService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * 回答接口：提交/查询（同步）+ 流式追问（SSE）。
 * SSE 事件协议：delta（增量文本）→ done（结束）或 error（失败）。
 * 归属校验在返回 SseEmitter 之前同步完成，未通过直接抛业务异常。
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/sessions/{sessionId}/questions/{questionId}")
@RequiredArgsConstructor
public class InterviewAnswerController {

  private static final long SSE_TIMEOUT_MILLIS = 120_000L;

  private final AnswerService answerService;
  private final LlmClientFactory llmClientFactory;

  @PostMapping("/answer")
  public Result<AnswerResponse> submit(@PathVariable Long sessionId, @PathVariable Long questionId,
                                       @Valid @RequestBody SubmitAnswerRequest request) {
    Long userId = SecurityUtils.requireCurrentUserId();
    return Result.ok(answerService.submitAnswer(sessionId, questionId, userId, request));
  }

  @GetMapping("/answer")
  public Result<AnswerResponse> detail(@PathVariable Long sessionId, @PathVariable Long questionId) {
    Long userId = SecurityUtils.requireCurrentUserId();
    return Result.ok(answerService.getAnswer(sessionId, questionId, userId));
  }

  @GetMapping(value = "/follow-up", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter followUp(@PathVariable Long sessionId, @PathVariable Long questionId) {
    Long userId = SecurityUtils.requireCurrentUserId();
    String prompt = answerService.buildFollowUpPrompt(sessionId, questionId, userId);

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    AtomicReference<Disposable> subscription = new AtomicReference<>();
    Disposable disposable = llmClientFactory.getChatClient()
        .prompt().user(prompt).stream().content()
        .subscribe(
            chunk -> {
              try {
                emitter.send(SseEmitter.event().name("delta").data(chunk, MediaType.TEXT_PLAIN));
              } catch (IOException e) {
                // 客户端断开：停止生成并收尾
                log.debug("SSE 发送失败，客户端可能已断开: {}", e.getMessage());
                Disposable current = subscription.get();
                if (current != null) {
                  current.dispose();
                }
                emitter.complete();
              }
            },
            error -> {
              log.warn("流式追问生成失败: sessionId={}, questionId={}, {}",
                  sessionId, questionId, error.getMessage());
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
