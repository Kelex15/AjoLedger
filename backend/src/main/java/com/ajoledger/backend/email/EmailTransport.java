package com.ajoledger.backend.email;

public interface EmailTransport {

    /** @throws EmailDeliveryException if the provider didn't accept the email */
    void send(OutboxEmail email);
}
