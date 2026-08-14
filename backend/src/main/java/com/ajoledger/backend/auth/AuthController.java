package com.ajoledger.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.UUID;

import com.ajoledger.backend.auth.dto.AuthResponseDTO;
import com.ajoledger.backend.auth.dto.GoogleLoginRequestDTO;
import com.ajoledger.backend.auth.dto.LoginRequestDTO;
import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.auth.dto.RefreshRequestDTO;
import com.ajoledger.backend.auth.dto.SignupRequestDTO;
import com.ajoledger.backend.auth.dto.VerifyEmailRequestDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(path = "signup", version = "1")
    public ResponseEntity<MessageResponseDTO> signup(@Valid @RequestBody SignupRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(dto));
    }

    @PostMapping(path = "verify-email", version = "1")
    public ResponseEntity<MessageResponseDTO> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO dto) {
        return ResponseEntity.ok(authService.verifyEmail(dto));
    }

    @PostMapping(path = "login", version = "1")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @PostMapping(path = "google", version = "1")
    public ResponseEntity<AuthResponseDTO> loginWithGoogle(@Valid @RequestBody GoogleLoginRequestDTO dto) {
        return ResponseEntity.ok(authService.loginWithGoogle(dto));
    }

    @PostMapping(path = "refresh", version = "1")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO dto) {
        return ResponseEntity.ok(authService.refresh(dto));
    }

    @PostMapping(path = "logout", version = "1")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UUID currentUserId, @Valid @RequestBody RefreshRequestDTO dto) {
        authService.logout(currentUserId, dto);
        return ResponseEntity.noContent().build();
    }
}
