package com.jio.eim.user.service;

import com.jio.eim.user.dto.LoginRequest;
import com.jio.eim.user.dto.LoginResponse;
import com.jio.eim.user.entity.AuthEvent;
import com.jio.eim.user.entity.User;
import com.jio.eim.user.repository.AuthEventRepository;
import com.jio.eim.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String LOGIN_FAILED = "LOGIN_FAILED";
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";

    private final UserRepository userRepository;
    private final AuthEventRepository authEventRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public AuthService(
            UserRepository userRepository,
            AuthEventRepository authEventRepository,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.authEventRepository = authEventRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            logEvent(null, LOGIN_FAILED, httpRequest, "Invalid credentials");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        if (!ACTIVE.equals(user.getStatus())) {
            logEvent(user.getId(), LOGIN_FAILED, httpRequest, "Account not active");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        }

        user.setLastLogin(Instant.now());
        userRepository.save(user);
        logEvent(user.getId(), LOGIN_SUCCESS, httpRequest, null);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(jwtService.generate(user));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtService.getExpiresInSeconds());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        return response;
    }

    private void logEvent(java.util.UUID userId, String eventType, HttpServletRequest request, String details) {
        AuthEvent event = new AuthEvent();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setIpAddress(request.getRemoteAddr());
        event.setUserAgent(request.getHeader("User-Agent"));
        event.setDetails(details);
        event.setCreatedAt(Instant.now());
        authEventRepository.save(event);
    }
}
