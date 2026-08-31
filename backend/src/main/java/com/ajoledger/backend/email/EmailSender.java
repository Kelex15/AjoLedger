package com.ajoledger.backend.email;

public interface EmailSender {

    void sendVerificationEmail(String toEmail, String verificationLink);
}
