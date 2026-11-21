package com.smartinterview.common.result;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 分页响应体，屏蔽 Spring Data Page 结构对外暴露。
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages());
  }
}
