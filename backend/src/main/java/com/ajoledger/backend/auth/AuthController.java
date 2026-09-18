package com.ajoledger.backend.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.UUID;

import com.ajoledger.backend.auth.dto.AuthResponseDTO;
import com.ajoledger.backend.auth.dto.GoogleLoginRequestDTO;
import com.ajoledger.backend.auth.dto.LoginRequestDTO;
import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.auth.dto.RefreshRequestDTO;
import com.ajoledger.backend.auth.dto.SignupRequestDTO;
import com.ajoledger.backend.auth.dto.VerifyEmailRequestDTO;
import com.ajoledger.backend.auth.exception.InvalidOrExpiredTokenException;
import com.ajoledger.backend.system.security.AuthCookieService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping(path = "signup", version = "1")
    public ResponseEntity<MessageResponseDTO> signup(@Valid @RequestBody SignupRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(dto));
    }

    @PostMapping(path = "verify-email", version = "1")
    public ResponseEntity<MessageResponseDTO> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO dto) {
        return ResponseEntity.ok(authService.verifyEmail(dto));
    }

    @PostMapping(path = "login", version = "1")
    public ResponseEntity<MessageResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return withAuthCookies(authService.login(dto));
    }

    @PostMapping(path = "google", version = "1")
    public ResponseEntity<MessageResponseDTO> loginWithGoogle(@Valid @RequestBody GoogleLoginRequestDTO dto) {
        return withAuthCookies(authService.loginWithGoogle(dto));
    }

    @PostMapping(path = "refresh", version = "1")
    public ResponseEntity<MessageResponseDTO> refresh(HttpServletRequest request) {
        String refreshToken = authCookieService.readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new InvalidOrExpiredTokenException("Missing refresh token"));
        return withAuthCookies(authService.refresh(new RefreshRequestDTO(refreshToken)));
    }

    @PostMapping(path = "logout", version = "1")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UUID currentUserId, HttpServletRequest request) {
        authCookieService.readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .ifPresent(refreshToken -> authService.logout(currentUserId, new RefreshRequestDTO(refreshToken)));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshTokenCookie().toString())
                .build();
    }

    private ResponseEntity<MessageResponseDTO> withAuthCookies(AuthResponseDTO tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.buildAccessTokenCookie(tokens.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookieService.buildRefreshTokenCookie(tokens.refreshToken()).toString())
                .body(new MessageResponseDTO("OK"));
    }
}
