package com.ajoledger.backend.user.dto;

import java.util.UUID;

import com.ajoledger.backend.user.User;

public record UserResponseDTO(UUID id, String username, String firstName, String lastName, String email) {

    public static UserResponseDTO fromUser(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail());
    }
}
