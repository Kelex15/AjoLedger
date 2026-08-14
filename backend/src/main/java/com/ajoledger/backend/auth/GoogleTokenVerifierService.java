package com.ajoledger.backend.auth;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import com.ajoledger.backend.system.AppProperties;

@Service
public class GoogleTokenVerifierService {

    public record GoogleUserInfo(String email, boolean emailVerified, String subject) {
    }

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifierService(AppProperties appProperties) throws GeneralSecurityException, IOException {
        this.verifier = new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(appProperties.google().clientId()))
                .build();
    }

    public Optional<GoogleUserInfo> verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            return Optional.of(new GoogleUserInfo(payload.getEmail(), emailVerified, payload.getSubject()));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
