package com.ajoledger.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink) {
        log.warn("ConsoleEmailSender is a local-dev placeholder. Not suitable for production. Swap in a real EmailSender before deploying.");
        log.info("Verification email for {}: {}", toEmail, verificationLink);
    }

    @Override
    public void sendDefaultNotificationEmail(String toEmail, String message) {
        log.info("Default notification email for {}: {}", toEmail, message);
    }
}
