package com.smartinterview.modules.user.service;

import com.smartinterview.common.exception.BusinessException;
import com.smartinterview.common.exception.ErrorCode;
import com.smartinterview.common.security.SecurityUtils;
import com.smartinterview.modules.user.mapper.UserMapper;
import com.smartinterview.modules.user.model.UserEntity;
import com.smartinterview.modules.user.model.UserResponse;
import com.smartinterview.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;

  @Transactional(readOnly = true)
  public UserResponse getCurrentUser() {
    Long userId = SecurityUtils.requireCurrentUserId();
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    return userMapper.toResponse(user);
  }
}
