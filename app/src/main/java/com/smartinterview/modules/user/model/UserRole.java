package com.smartinterview.modules.user.model;

/**
 * 用户角色，RBAC 基础。安全上下文中以 ROLE_ 前缀暴露给 @PreAuthorize。
 */
public enum UserRole {
  USER,
  ADMIN
}
