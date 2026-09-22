package com.ajoledger.backend.ledger.idempotency;

public final class IdempotencyKeys {

    private static final String DELIMITER = ":";

    private IdempotencyKeys() {
    }

    public static String of(Object base, Object... parts) {
        String key = requireNonBlank(base);
        for (Object part : parts) {
            key += DELIMITER + requirePart(part);
        }
        return key;
    }

    private static String requireNonBlank(Object value) {
        String string = String.valueOf(value);
        if (string.isBlank()) {
            throw new IllegalArgumentException("Idempotency key base must not be blank");
        }
        return string;
    }

    private static String requirePart(Object value) {
        String string = requireNonBlank(value);
        if (string.contains(DELIMITER)) {
            throw new IllegalArgumentException("Idempotency key part must not contain '" + DELIMITER + "': " + string);
        }
        return string;
    }
}
