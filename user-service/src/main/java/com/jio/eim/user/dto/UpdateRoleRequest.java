package com.jio.eim.user.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateRoleRequest {

    @NotBlank
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
