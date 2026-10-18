package com.ajoledger.backend.system;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Objects;
import java.util.stream.Collectors;

import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.auth.exception.AccountLockedException;
import com.ajoledger.backend.auth.exception.DemoUnavailableException;
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
import com.ajoledger.backend.funding.paypal.exception.PayPalOrderFailedException;
import com.ajoledger.backend.funding.paypal.exception.PayPalOrderNotFoundException;
import com.ajoledger.backend.funding.stripe.exception.CheckoutSessionNotFoundException;
import com.ajoledger.backend.funding.stripe.exception.StripeCheckoutFailedException;
import com.stripe.exception.SignatureVerificationException;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.ledger.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.ledger.exception.InvalidTransferTargetException;
import com.ajoledger.backend.ledger.exception.LienWithdrawalBlockedException;
import com.ajoledger.backend.user.exception.UserNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<MessageResponseDTO> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<MessageResponseDTO> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(new MessageResponseDTO(e.getMessage()));
    }

    @ExceptionHandler(DemoUnavailableException.class)
    public ResponseEntity<MessageResponseDTO> handleDemoUnavailable(DemoUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new MessageResponseDTO(e.getMessage()));
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

    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<MessageResponseDTO> handleStripeSignatureVerificationFailed(SignatureVerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponseDTO("Invalid webhook signature"));
    }

    /** A processor's own diagnostics can name credentials, endpoints and account state, so the
     * client gets a fixed message and the detail stays in the log. */
    @ExceptionHandler({StripeCheckoutFailedException.class, PayPalOrderFailedException.class})
    public ResponseEntity<MessageResponseDTO> handlePaymentProviderFailed(RuntimeException e) {
        log.error("Payment provider call failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new MessageResponseDTO("Could not reach the payment provider. Please try again."));
    }

    @ExceptionHandler({PayPalOrderNotFoundException.class, CheckoutSessionNotFoundException.class})
    public ResponseEntity<MessageResponseDTO> handleCheckoutReferenceNotFound(RuntimeException e) {
        log.info("Checkout reference not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDTO("Deposit not found"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<MessageResponseDTO> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDTO("A conflicting record already exists"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponseDTO> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MessageResponseDTO("Something went wrong. Please try again."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"))
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, new MessageResponseDTO(message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                               HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof MessageResponseDTO) {
            return ResponseEntity.status(statusCode).headers(headers).body(body);
        }
        String message = Objects.requireNonNullElse(ex.getMessage(), statusCode.toString());
        return ResponseEntity.status(statusCode).headers(headers).body(new MessageResponseDTO(message));
    }
}
