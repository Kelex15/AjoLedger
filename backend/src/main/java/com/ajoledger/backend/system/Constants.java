package com.ajoledger.backend.system;

import java.util.UUID;

public final class Constants {

    private Constants() {
    }

    public static final class Account {

        private Account() {
        }

        /** The dedicated system user (seeded in V5) that owns platform-wide singleton accounts (SYSTEM_BUFFER, CLEARING_*). */
        public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
