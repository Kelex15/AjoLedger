package com.ajoledger.backend.system;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.stream.Collectors;

import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.auth.exception.AccountLockedException;
import com.ajoledger.backend.auth.exception.EmailNotVerifiedException;
import com.ajoledger.backend.auth.exception.InvalidCredentialsException;
import com.ajoledger.backend.auth.exception.InvalidOrExpiredTokenException;
import com.ajoledger.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.ajoledger.backend.circle.exception.CircleActionNotAllowedException;
import com.ajoledger.backend.circle.exception.CircleCreatorRequiredException;
import com.ajoledger.backend.circle.exception.CircleNotFoundException;
import com.ajoledger.backend.circle.exception.InviteAlreadyRedeemedException;
import com.ajoledger.backend.circle.exception.InviteIdentityMismatchException;
import com.ajoledger.backend.circle.exception.InviteNotFoundException;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.ledger.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.ledger.exception.InvalidTransferTargetException;
import com.ajoledger.backend.ledger.exception.LienWithdrawalBlockedException;
import com.ajoledger.backend.user.exception.UserNotFoundException;

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

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<MessageResponseDTO> handleInsufficientBalance(InsufficientBalanceException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<MessageResponseDTO> handleIdempotencyKeyConflict(IdempotencyKeyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<MessageResponseDTO> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(LienWithdrawalBlockedException.class)
    public ResponseEntity<MessageResponseDTO> handleLienWithdrawalBlocked(LienWithdrawalBlockedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(InvalidTransferTargetException.class)
    public ResponseEntity<MessageResponseDTO> handleInvalidTransferTarget(InvalidTransferTargetException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<MessageResponseDTO> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(CircleNotFoundException.class)
    public ResponseEntity<MessageResponseDTO> handleCircleNotFound(CircleNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(CircleActionNotAllowedException.class)
    public ResponseEntity<MessageResponseDTO> handleCircleActionNotAllowed(CircleActionNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(CircleCreatorRequiredException.class)
    public ResponseEntity<MessageResponseDTO> handleCircleCreatorRequired(CircleCreatorRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(InviteNotFoundException.class)
    public ResponseEntity<MessageResponseDTO> handleInviteNotFound(InviteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(InviteAlreadyRedeemedException.class)
    public ResponseEntity<MessageResponseDTO> handleInviteAlreadyRedeemed(InviteAlreadyRedeemedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(InviteIdentityMismatchException.class)
    public ResponseEntity<MessageResponseDTO> handleInviteIdentityMismatch(InviteIdentityMismatchException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDTO(e.getMessage()));
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
