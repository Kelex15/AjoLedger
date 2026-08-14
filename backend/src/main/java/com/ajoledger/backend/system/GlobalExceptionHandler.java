package com.ajoledger.backend.system;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.stream.Collectors;

import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.auth.exception.AccountLockedException;
import com.ajoledger.backend.auth.exception.EmailNotVerifiedException;
import com.ajoledger.backend.auth.exception.InvalidCredentialsException;
import com.ajoledger.backend.auth.exception.InvalidOrExpiredTokenException;
import com.ajoledger.backend.auth.exception.RefreshTokenReuseDetectedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<MessageResponseDTO> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<MessageResponseDTO> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<MessageResponseDTO> handleEmailNotVerified(EmailNotVerifiedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(InvalidOrExpiredTokenException.class)
    public ResponseEntity<MessageResponseDTO> handleInvalidOrExpiredToken(InvalidOrExpiredTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(RefreshTokenReuseDetectedException.class)
    public ResponseEntity<MessageResponseDTO> handleRefreshTokenReuse(RefreshTokenReuseDetectedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponseDTO("Invalid refresh token"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<MessageResponseDTO> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO("A conflicting record already exists"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponseDTO> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"))
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponseDTO(message));
    }
}
