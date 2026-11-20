package com.smartinterview.modules.user.controller;

import com.smartinterview.common.result.Result;
import com.smartinterview.modules.user.model.UserResponse;
import com.smartinterview.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public Result<UserResponse> me() {
    return Result.ok(userService.getCurrentUser());
  }
}
