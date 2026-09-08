package com.ajoledger.backend.email;

public interface EmailSender {

    void sendVerificationEmail(String toEmail, String verificationLink);

    void sendDefaultNotificationEmail(String toEmail, String message);
}
