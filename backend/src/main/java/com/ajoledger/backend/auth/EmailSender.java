package com.ajoledger.backend.auth;

public interface EmailSender {

    void sendVerificationEmail(String toEmail, String verificationLink);
}
