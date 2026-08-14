package com.ajoledger.backend.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.ajoledger.backend.auth.exception.InvalidOrExpiredTokenException;
import com.ajoledger.backend.auth.exception.RefreshTokenReuseDetectedException;
import com.ajoledger.backend.system.AppProperties;
import com.ajoledger.backend.user.User;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMillis;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AppProperties appProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMillis = appProperties.jwt().refreshTokenExpiration();
    }

    public record RotatedToken(User user, String rawToken) {
    }

    public String issue(User user) {
        return issueForFamily(user, UUID.randomUUID()).rawToken();
    }

    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public RotatedToken rotate(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidOrExpiredTokenException("Invalid refresh token"));

        if (existing.isRevoked()) {
            revokeFamily(existing.getFamilyId());
            throw new RefreshTokenReuseDetectedException(existing.getUser(), existing.getFamilyId());
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOrExpiredTokenException("Refresh token expired");
        }

        IssuedToken newToken = issueForFamily(existing.getUser(), existing.getFamilyId());

        existing.setRevoked(true);
        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedByTokenId(newToken.id());
        refreshTokenRepository.save(existing);

        return new RotatedToken(existing.getUser(), newToken.rawToken());
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    private void revokeFamily(UUID familyId) {
        List<RefreshToken> active = refreshTokenRepository.findByFamilyIdAndRevokedFalse(familyId);
        LocalDateTime now = LocalDateTime.now();
        for (RefreshToken token : active) {
            token.setRevoked(true);
            token.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(active);
    }

    private record IssuedToken(UUID id, String rawToken) {
    }

    private IssuedToken issueForFamily(User user, UUID familyId) {
        String rawToken = generateOpaqueToken();
        LocalDateTime now = LocalDateTime.now();

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .familyId(familyId)
                .tokenHash(hash(rawToken))
                .issuedAt(now)
                .expiresAt(now.plus(refreshTokenExpirationMillis, ChronoUnit.MILLIS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);

        return new IssuedToken(entity.getId(), rawToken);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
