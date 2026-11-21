package com.smartinterview.modules.knowledgebase.controller;

import com.smartinterview.common.result.PageResponse;
import com.smartinterview.common.result.Result;
import com.smartinterview.common.security.SecurityUtils;
import com.smartinterview.modules.knowledgebase.model.CreateKbRequest;
import com.smartinterview.modules.knowledgebase.model.KbDocumentResponse;
import com.smartinterview.modules.knowledgebase.model.KbResponse;
import com.smartinterview.modules.knowledgebase.service.DocumentService;
import com.smartinterview.modules.knowledgebase.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@Validated
public class KnowledgeBaseController {

  private final KnowledgeBaseService kbService;
  private final DocumentService documentService;

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
}
