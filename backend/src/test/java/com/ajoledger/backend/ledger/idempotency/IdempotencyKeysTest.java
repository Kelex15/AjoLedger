package com.ajoledger.backend.ledger.idempotency;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeysTest {

    @Test
    void joinsBaseAndPartsWithColon() {
        assertThat(IdempotencyKeys.of("abc", "join")).isEqualTo("abc:join");
        assertThat(IdempotencyKeys.of("abc", "round-1", "buffer-fee")).isEqualTo("abc:round-1:buffer-fee");
    }

    @Test
    void acceptsNonStringSegments() {
        UUID membershipId = UUID.randomUUID();

        assertThat(IdempotencyKeys.of(membershipId, "join")).isEqualTo(membershipId + ":join");
    }

    @Test
    void rejectsBlankSegments() {
        assertThatThrownBy(() -> IdempotencyKeys.of("", "join")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyKeys.of("abc", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPartsContainingTheDelimiter() {
        assertThatThrownBy(() -> IdempotencyKeys.of("abc", "buffer:fee")).isInstanceOf(IllegalArgumentException.class);
    }
}
