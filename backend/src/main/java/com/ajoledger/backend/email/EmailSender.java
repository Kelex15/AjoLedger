package com.ajoledger.backend.email;

import java.time.Instant;

public interface EmailSender {

    void sendVerificationEmail(String toEmail, String verificationLink, Instant linkExpiresAt);

    void sendDefaultNotificationEmail(String toEmail, String message);
}
