package com.jio.eim.user.controller;

import com.jio.eim.user.dto.ApiResponse;
import com.jio.eim.user.dto.LoginRequest;
import com.jio.eim.user.dto.LoginResponse;
import com.jio.eim.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse data = authService.login(request, httpRequest);
        return ApiResponse.ok("Login successful", data);
    }
}
