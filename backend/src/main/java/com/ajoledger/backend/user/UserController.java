package com.ajoledger.backend.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.ajoledger.backend.user.dto.UserResponseDTO;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping(path = "/me", version = "1")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal UUID currentUserId) {
        return userService.getUserById(currentUserId)
                .map(UserResponseDTO::fromUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/{id}", version = "1")
    public ResponseEntity<UserResponseDTO> getUser(@PathVariable UUID id) {
        return userService.getUserById(id)
                .map(UserResponseDTO::fromUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}