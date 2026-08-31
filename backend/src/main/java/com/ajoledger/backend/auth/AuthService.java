package com.ajoledger.backend.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.ajoledger.backend.auth.audit.AuditEventType;
import com.ajoledger.backend.auth.audit.AuditLog;
import com.ajoledger.backend.auth.audit.AuditLogRepository;
import com.ajoledger.backend.auth.dto.AuthResponseDTO;
import com.ajoledger.backend.auth.dto.GoogleLoginRequestDTO;
import com.ajoledger.backend.auth.dto.LoginRequestDTO;
import com.ajoledger.backend.auth.dto.MessageResponseDTO;
import com.ajoledger.backend.auth.dto.RefreshRequestDTO;
import com.ajoledger.backend.auth.dto.SignupRequestDTO;
import com.ajoledger.backend.auth.dto.VerifyEmailRequestDTO;
import com.ajoledger.backend.auth.exception.AccountLockedException;
import com.ajoledger.backend.auth.exception.EmailNotVerifiedException;
import com.ajoledger.backend.auth.exception.InvalidCredentialsException;
import com.ajoledger.backend.auth.exception.InvalidOrExpiredTokenException;
import com.ajoledger.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.ajoledger.backend.auth.refreshtoken.RefreshTokenService;
import com.ajoledger.backend.email.EmailSender;
import com.ajoledger.backend.system.AppProperties;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final long VERIFICATION_TOKEN_VALIDITY_HOURS = 24;

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    public AuthService(UserRepository userRepository, UserAuthRepository userAuthRepository,
                        AuditLogRepository auditLogRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, RefreshTokenService refreshTokenService,
                        GoogleTokenVerifierService googleTokenVerifierService, EmailSender emailSender,
                        AppProperties appProperties) {
        this.userRepository = userRepository;
        this.userAuthRepository = userAuthRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
    }

    @Transactional
    public MessageResponseDTO signup(SignupRequestDTO dto) {
        String email = dto.getEmail().toLowerCase();

        reclaimStaleUnverifiedAccount(email);

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new DataIntegrityViolationException("Username or email already registered");
        }

        User user = userRepository.save(User.builder()
                .username(dto.getUsername())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(email)
                .build());

        String verificationToken = UUID.randomUUID().toString();
        userAuthRepository.save(UserAuth.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .emailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiresAt(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_VALIDITY_HOURS))
                .failedLoginAttempts(0)
                .build());

        String verificationLink = appProperties.frontend().baseUrl() + "/verify-email?token=" + verificationToken;
        emailSender.sendVerificationEmail(email, verificationLink);

        audit(user, AuditEventType.SIGNUP, null);

        return new MessageResponseDTO("Account created. Check your email to verify your account before logging in.");
    }

    /**
     * A signup that never got verified doesn't get to permanently squat on an email -
     * deletes it so the real owner can retry, since only they can complete verification anyway.
     * A verified account is a real conflict and is left alone.
     */
    private void reclaimStaleUnverifiedAccount(String email) {
        userRepository.findByEmail(email).ifPresent(existingUser -> {
            UserAuth existingAuth = userAuthRepository.findByUserId(existingUser.getId()).orElse(null);
            if (existingAuth != null && existingAuth.isEmailVerified()) {
                throw new DataIntegrityViolationException("Username or email already registered");
            }
            // explicit flush: Hibernate's default flush order runs all inserts before all deletes
            // within one transaction, so without this the new user's insert (below) would hit
            // the unique email constraint before this delete ever reaches the database
            if (existingAuth != null) {
                userAuthRepository.delete(existingAuth);
                userAuthRepository.flush();
            }
            userRepository.delete(existingUser);
            userRepository.flush();
        });
    }

    @Transactional(noRollbackFor = InvalidOrExpiredTokenException.class)
    public MessageResponseDTO verifyEmail(VerifyEmailRequestDTO dto) {
        UserAuth userAuth = userAuthRepository.findByVerificationToken(dto.getToken())
                .orElseThrow(() -> new InvalidOrExpiredTokenException("Invalid or expired verification token"));

        if (userAuth.getVerificationTokenExpiresAt() == null
                || userAuth.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            User staleUser = userAuth.getUser();
            userAuthRepository.delete(userAuth);
            userRepository.delete(staleUser);
            throw new InvalidOrExpiredTokenException("Verification link expired - please sign up again");
        }

        userAuth.setEmailVerified(true);
        userAuth.setVerificationToken(null);
        userAuth.setVerificationTokenExpiresAt(null);
        userAuthRepository.save(userAuth);

        audit(userAuth.getUser(), AuditEventType.EMAIL_VERIFIED, null);

        return new MessageResponseDTO("Email verified. You can now log in.");
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class, EmailNotVerifiedException.class})
    public AuthResponseDTO login(LoginRequestDTO dto) {
        String email = dto.getEmail().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            audit(null, AuditEventType.LOGIN_FAILURE, "no_account_for_email");
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        User user = userOpt.get();

        UserAuth userAuth = userAuthRepository.findByUserId(user.getId())
                .orElseThrow(() -> {
                    audit(user, AuditEventType.LOGIN_FAILURE, "no_credentials_record");
                    return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
                });

        if (isLocked(userAuth)) {
            audit(user, AuditEventType.LOGIN_FAILURE, "account_locked");
            throw new AccountLockedException("Account is temporarily locked due to too many failed login attempts");
        }

        if (!passwordEncoder.matches(dto.getPassword(), userAuth.getPasswordHash())) {
            recordFailedAttempt(user, userAuth);
            throw isLocked(userAuth)
                    ? new AccountLockedException("Account is temporarily locked due to too many failed login attempts")
                    : new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        resetFailedAttempts(userAuth);

        if (!userAuth.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        audit(user, AuditEventType.LOGIN_SUCCESS, "password");
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, EmailNotVerifiedException.class})
    public AuthResponseDTO loginWithGoogle(GoogleLoginRequestDTO dto) {
        GoogleTokenVerifierService.GoogleUserInfo googleUser = googleTokenVerifierService.verify(dto.getIdToken())
                .orElseThrow(() -> {
                    audit(null, AuditEventType.LOGIN_FAILURE, "invalid_google_token");
                    return new InvalidCredentialsException("Invalid Google token");
                });

        if (!googleUser.emailVerified()) {
            audit(null, AuditEventType.LOGIN_FAILURE, "google_email_not_verified");
            throw new InvalidCredentialsException("Google account email is not verified");
        }

        String email = googleUser.email().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    audit(null, AuditEventType.LOGIN_FAILURE, "no_account_for_google_email");
                    return new InvalidCredentialsException("No account found for this email - please sign up first");
                });

        UserAuth userAuth = userAuthRepository.findByUserId(user.getId())
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

        if (!userAuth.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        if (userAuth.getGoogleId() == null) {
            userAuth.setGoogleId(googleUser.subject());
            userAuthRepository.save(userAuth);
            audit(user, AuditEventType.GOOGLE_LINKED, null);
        } else if (!userAuth.getGoogleId().equals(googleUser.subject())) {
            audit(user, AuditEventType.LOGIN_FAILURE, "google_account_mismatch");
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        audit(user, AuditEventType.LOGIN_SUCCESS, "google");
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public AuthResponseDTO refresh(RefreshRequestDTO dto) {
        RefreshTokenService.RotatedToken rotated;
        try {
            rotated = refreshTokenService.rotate(dto.getRefreshToken());
        } catch (RefreshTokenReuseDetectedException e) {
            audit(e.getUser(), AuditEventType.REFRESH_TOKEN_REUSE_DETECTED, "family_id=" + e.getFamilyId());
            throw e;
        }

        String accessToken = jwtService.generateAccessToken(rotated.user());
        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(rotated.rawToken())
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .build();
    }

    @Transactional
    public void logout(UUID currentUserId, RefreshRequestDTO dto) {
        refreshTokenService.revoke(dto.getRefreshToken());
        userRepository.findById(currentUserId).ifPresent(user -> audit(user, AuditEventType.LOGOUT, null));
    }

    private AuthResponseDTO issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .build();
    }

    private boolean isLocked(UserAuth userAuth) {
        return userAuth.getLockedUntil() != null && userAuth.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void recordFailedAttempt(User user, UserAuth userAuth) {
        userAuth.setFailedLoginAttempts(userAuth.getFailedLoginAttempts() + 1);
        userAuth.setLastFailedLoginAt(LocalDateTime.now());

        if (userAuth.getFailedLoginAttempts() >= appProperties.security().maxFailedAttempts()) {
            userAuth.setLockedUntil(LocalDateTime.now().plusMinutes(appProperties.security().lockoutDurationMinutes()));
            userAuthRepository.save(userAuth);
            audit(user, AuditEventType.ACCOUNT_LOCKED, null);
        } else {
            userAuthRepository.save(userAuth);
            audit(user, AuditEventType.LOGIN_FAILURE, "invalid_password");
        }
    }

    private void resetFailedAttempts(UserAuth userAuth) {
        userAuth.setFailedLoginAttempts(0);
        userAuth.setLastFailedLoginAt(null);
        userAuth.setLockedUntil(null);
        userAuthRepository.save(userAuth);
    }

    private void audit(User user, AuditEventType eventType, String detail) {
        auditLogRepository.save(AuditLog.builder()
                .user(user)
                .eventType(eventType)
                .detail(detail)
                .build());
    }
}
