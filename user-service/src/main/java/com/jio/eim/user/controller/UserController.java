package com.jio.eim.user.controller;

import com.jio.eim.user.dto.ApiResponse;
import com.jio.eim.user.dto.CreateUserRequest;
import com.jio.eim.user.dto.UpdateRoleRequest;
import com.jio.eim.user.dto.UserResponse;
import com.jio.eim.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok("User created", userService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER')")
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok("Users retrieved", userService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER')")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok("User retrieved", userService.get(id));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Role updated", userService.updateRole(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.ok("User deactivated", userService.deactivate(id));
    }
}
