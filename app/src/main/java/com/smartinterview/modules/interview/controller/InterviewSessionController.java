package com.smartinterview.modules.interview.controller;

import com.smartinterview.common.result.PageResponse;
import com.smartinterview.common.result.Result;
import com.smartinterview.common.security.SecurityUtils;
import com.smartinterview.modules.interview.model.CreateSessionRequest;
import com.smartinterview.modules.interview.model.QuestionResponse;
import com.smartinterview.modules.interview.model.SessionResponse;
import com.smartinterview.modules.interview.service.InterviewReportService;
import com.smartinterview.modules.interview.service.InterviewSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview/sessions")
@RequiredArgsConstructor
@Validated
public class InterviewSessionController {

  private final InterviewSessionService sessionService;
  private final InterviewReportService reportService;

  @PostMapping
  public Result<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
    return Result.ok(sessionService.createSession(request, SecurityUtils.requireCurrentUserId()));
  }

  @GetMapping
  public Result<PageResponse<SessionResponse>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return Result.ok(sessionService.listSessions(SecurityUtils.requireCurrentUserId(), pageable));
  }

  @GetMapping("/{id}")
  public Result<SessionResponse> detail(@PathVariable Long id) {
    return Result.ok(sessionService.getSession(id, SecurityUtils.requireCurrentUserId()));
  }

  /**
   * 面试报告 PDF 导出。二进制下载不走 Result 包装；
   * 归属校验在 Service 内完成，越权时全局异常处理器兜底。
   */
  @GetMapping("/{id}/report.pdf")
  public ResponseEntity<byte[]> report(@PathVariable Long id) {
    byte[] pdf = reportService.generateReport(id, SecurityUtils.requireCurrentUserId());
    String filename = "interview-report-" + id + ".pdf";
    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  @PostMapping("/{id}/questions/regenerate")
  public Result<SessionResponse> regenerate(@PathVariable Long id) {
    return Result.ok(sessionService.regenerateQuestions(id, SecurityUtils.requireCurrentUserId()));
  }

  @GetMapping("/{id}/questions")
  public Result<List<QuestionResponse>> questions(@PathVariable Long id) {
    return Result.ok(sessionService.listQuestions(id, SecurityUtils.requireCurrentUserId()));
  }
}
