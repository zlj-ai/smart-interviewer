package com.smartinterview.modules.resume.controller;

import com.smartinterview.common.result.PageResponse;
import com.smartinterview.common.result.Result;
import com.smartinterview.common.security.SecurityUtils;
import com.smartinterview.modules.resume.model.ResumeDetailResponse;
import com.smartinterview.modules.resume.model.ResumeResponse;
import com.smartinterview.modules.resume.service.ResumeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
@Validated
public class ResumeController {

  private final ResumeService resumeService;

  @PostMapping
  public Result<ResumeResponse> upload(@RequestParam("file") MultipartFile file) {
    return Result.ok(resumeService.upload(SecurityUtils.requireCurrentUserId(), file));
  }

  @GetMapping
  public Result<PageResponse<ResumeResponse>> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return Result.ok(resumeService.list(SecurityUtils.requireCurrentUserId(), pageable));
  }

  @GetMapping("/{id}")
  public Result<ResumeDetailResponse> detail(@PathVariable Long id) {
    return Result.ok(resumeService.getDetail(id, SecurityUtils.requireCurrentUserId()));
  }

  @PostMapping("/{id}/analyze")
  public Result<ResumeResponse> reanalyze(@PathVariable Long id) {
    return Result.ok(resumeService.reanalyze(id, SecurityUtils.requireCurrentUserId()));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    resumeService.delete(id, SecurityUtils.requireCurrentUserId());
    return Result.ok();
  }
}
