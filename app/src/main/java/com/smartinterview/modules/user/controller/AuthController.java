package com.smartinterview.modules.user.controller;

import com.smartinterview.common.result.Result;
import com.smartinterview.modules.user.model.LoginRequest;
import com.smartinterview.modules.user.model.LoginResponse;
import com.smartinterview.modules.user.model.RegisterRequest;
import com.smartinterview.modules.user.model.TokenPairResponse;
import com.smartinterview.modules.user.model.TokenRefreshRequest;
import com.smartinterview.modules.user.model.UserResponse;
import com.smartinterview.modules.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    return Result.ok(authService.register(request));
  }

  @PostMapping("/login")
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.ok(authService.login(request));
  }

  @PostMapping("/refresh")
  public Result<TokenPairResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
    return Result.ok(authService.refresh(request));
  }

  @PostMapping("/logout")
  public Result<Void> logout(@Valid @RequestBody TokenRefreshRequest request) {
    authService.logout(request);
    return Result.ok();
  }
}
