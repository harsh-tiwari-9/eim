package com.jio.eim.user.service;

import com.jio.eim.user.dto.CreateUserRequest;
import com.jio.eim.user.dto.UpdateRoleRequest;
import com.jio.eim.user.dto.UserResponse;
import com.jio.eim.user.entity.User;
import com.jio.eim.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setStatus(ACTIVE);
        user.setCreatedAt(Instant.now());

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .filter(u -> !DELETED.equals(u.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateRole(UUID id, UpdateRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(request.getRole());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse deactivate(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setStatus(DELETED);
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLogin(user.getLastLogin());
        return response;
    }
}
