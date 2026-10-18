package com.ajoledger.backend.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Queues instead of sending, so an email exists only if the caller's transaction commits.
 * {@link EmailDeliveryService} does the actual sending. */
@Service
@RequiredArgsConstructor
public class OutboxEmailSender implements EmailSender {

    private final EmailOutboxDao emailOutboxDao;

    @Override
    public void sendVerificationEmail(String toEmail, String verificationLink, Instant linkExpiresAt) {
        String body = """
            Welcome to AjoLedger. Confirm this is your email address to finish setting up your account:

            %s

            If the link has expired, you can request a new one from the sign-in page. If you didn't sign up for AjoLedger, you can ignore this email.
            """.formatted(verificationLink);
        // Past the link's expiry the email is useless, so delivery stops trying.
        enqueue(toEmail, "Verify your AjoLedger email", body, linkExpiresAt);
    }

    @Override
    public void sendDefaultNotificationEmail(String toEmail, String message) {
        enqueue(toEmail, "An update from AjoLedger", message, null);
    }

    /** .invalid is a reserved TLD that can never resolve, which is why the demo accounts use it. Sending
     * there would only bounce and count against the sending domain's reputation. */
    private void enqueue(String toEmail, String subject, String body, Instant expiresAt) {
        if (toEmail.endsWith(".invalid")) {
            return;
        }
        emailOutboxDao.enqueue(toEmail, subject, body, expiresAt);
    }
}
