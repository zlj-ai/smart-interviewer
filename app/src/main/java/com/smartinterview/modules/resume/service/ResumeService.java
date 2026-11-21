package com.smartinterview.modules.resume.service;

import com.smartinterview.common.async.StreamTaskProducer;
import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.result.PageResponse;
import com.smartinterview.common.transaction.TransactionalExecutor;
import com.smartinterview.infrastructure.storage.ObjectStorageService;
import com.smartinterview.modules.resume.async.ResumeAnalysisStreamConsumer;
import com.smartinterview.modules.resume.async.ResumeAnalysisTask;
import com.smartinterview.modules.resume.mapper.ResumeMapper;
import com.smartinterview.modules.resume.model.ResumeDetailResponse;
import com.smartinterview.modules.resume.model.ResumeEntity;
import com.smartinterview.modules.resume.model.ResumeResponse;
import com.smartinterview.modules.resume.model.ResumeStatus;
import com.smartinterview.modules.resume.repository.ResumeRepository;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务：上传（对象存储 + 元数据入库）→ 发布分析任务。
 * 重新发起分析递增 attempt，评估回写按 attempt fencing。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

  private final ResumeRepository resumeRepository;
  private final ResumeMapper mapper;
  private final ObjectStorageService storageService;
  private final TransactionalExecutor transactionalExecutor;
  private final StreamTaskProducer taskProducer;

  public ResumeResponse upload(Long userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.PARAM_INVALID, "上传文件为空");
    }
    String objectKey = "resumes/" + userId + "/" + UUID.randomUUID()
        + "-" + resolveFilename(file);
    byte[] content = readBytes(file);
    storageService.put(objectKey, content,
        file.getContentType() == null ? "application/octet-stream" : file.getContentType());

    ResumeEntity entity = new ResumeEntity();
    entity.setUserId(userId);
    entity.setFilename(resolveFilename(file));
    entity.setObjectKey(objectKey);
    entity.setSizeBytes(file.getSize());
    entity.setCharCount(0);
    entity.setStatus(ResumeStatus.UPLOADED);
    entity.setAttempt(1);
    ResumeEntity saved = transactionalExecutor.inTransaction(() -> resumeRepository.save(entity));

    try {
      taskProducer.publish(ResumeAnalysisStreamConsumer.STREAM_KEY,
          new ResumeAnalysisTask(saved.getId(), saved.getAttempt()));
    } catch (BusinessException e) {
      log.error("分析任务发布失败，简历保留 UPLOADED 状态: resumeId={}, {}", saved.getId(), e.getMessage());
    }
    return mapper.toResponse(saved);
  }

  public ResumeResponse reanalyze(Long resumeId, Long userId) {
    ResumeEntity resume = loadOwned(resumeId, userId);
    resume.setStatus(ResumeStatus.UPLOADED);
    resume.setAttempt(resume.getAttempt() + 1);
    ResumeEntity saved = transactionalExecutor.inTransaction(() -> resumeRepository.save(resume));
    taskProducer.publish(ResumeAnalysisStreamConsumer.STREAM_KEY,
        new ResumeAnalysisTask(saved.getId(), saved.getAttempt()));
    return mapper.toResponse(saved);
  }

  public ResumeResponse get(Long resumeId, Long userId) {
    return mapper.toResponse(loadOwned(resumeId, userId));
  }

  public ResumeDetailResponse getDetail(Long resumeId, Long userId) {
    return mapper.toDetailResponse(loadOwned(resumeId, userId));
  }

  public PageResponse<ResumeResponse> list(Long userId, Pageable pageable) {
    Page<ResumeResponse> page = resumeRepository.findByUserId(userId, pageable)
        .map(mapper::toResponse);
    return PageResponse.from(page);
  }

  public void delete(Long resumeId, Long userId) {
    ResumeEntity resume = loadOwned(resumeId, userId);
    transactionalExecutor.inTransaction(() -> resumeRepository.delete(resume));
    try {
      storageService.delete(resume.getObjectKey());
    } catch (Exception e) {
      log.error("简历对象删除失败（孤儿对象不影响正确性）: objectKey={}, {}",
          resume.getObjectKey(), e.getMessage());
    }
  }

  public boolean existsResume(Long resumeId) {
    return resumeRepository.existsById(resumeId);
  }

  private ResumeEntity loadOwned(Long resumeId, Long userId) {
    return resumeRepository.findById(resumeId)
        .filter(r -> r.getUserId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "简历不存在"));
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.PARAM_INVALID, "读取上传文件失败: " + e.getMessage(), e);
    }
  }

  private String resolveFilename(MultipartFile file) {
    String name = file.getOriginalFilename();
    return name == null || name.isBlank() ? "unnamed" : name;
  }
}
