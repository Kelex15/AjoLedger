package com.ajoledger.backend.email.transport;

import com.ajoledger.backend.email.OutboxEmail;

public interface EmailTransport {

    /** @throws EmailDeliveryException if the provider didn't accept the email */
    void send(OutboxEmail email);
}
