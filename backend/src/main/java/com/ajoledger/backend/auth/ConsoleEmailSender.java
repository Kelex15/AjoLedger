package com.ajoledger.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.warn("ConsoleEmailSender is a local-dev placeholder - not suitable for production. Swap in a real EmailSender before deploying.");
        log.info("Verification email for {}: {}", toEmail, verificationLink);
    }
}
