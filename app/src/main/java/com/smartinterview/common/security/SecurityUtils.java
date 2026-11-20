package com.smartinterview.common.security;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 读取当前认证用户的静态入口。
 * 注意：虚拟线程下 ThreadLocal 仍可用，但异步任务中不得依赖它，需显式传递 userId。
 */
public final class SecurityUtils {

  private SecurityUtils() {
  }

  public static CurrentUser requireCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
      return currentUser;
    }
    throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
  }

  public static Long requireCurrentUserId() {
    return requireCurrentUser().userId();
  }
}
