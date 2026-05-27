package com.jio.eim.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String username;
    private String email;
    private String role;
}