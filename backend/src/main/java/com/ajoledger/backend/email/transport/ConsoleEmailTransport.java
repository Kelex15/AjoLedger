package com.ajoledger.backend.email.transport;

import com.ajoledger.backend.email.OutboxEmail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ajoledger.email", name = "provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailTransport implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailTransport.class);

    @Override
    public void send(OutboxEmail email) {
        log.info("Email to {} ({}):\n{}", email.recipient(), email.subject(), email.body());
    }
}
