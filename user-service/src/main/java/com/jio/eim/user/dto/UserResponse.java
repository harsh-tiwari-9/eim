package com.jio.eim.user.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private Instant createdAt;
    private Instant lastLogin;
}