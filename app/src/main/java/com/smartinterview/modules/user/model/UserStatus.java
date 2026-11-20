package com.smartinterview.modules.user.model;

/**
 * 用户状态。DISABLED 的账号登录直接拒绝，存量 access token 在其短时效内自然失效。
 */
public enum UserStatus {
  ACTIVE,
  DISABLED
}
