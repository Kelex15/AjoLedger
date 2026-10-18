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

    public static final class Demo {

        private Demo() {
        }

        /** The shared read-only demo account (ADR 0013). The seeder claims it on first boot, and the unique
         * constraint on username keeps anyone else from registering it afterwards. */
        public static final String USERNAME = "demo";
    }

    public static final class Circle {

        private Circle() {
        }

        /** Used when a circle's creator doesn't specify their own minimum member count. */
        public static final int DEFAULT_MIN_MEMBERS = 2;

        /** OPEN_ENROLLMENT circles must give people at least this long to actually consider joining. */
        public static final int MIN_ENROLLMENT_LEAD_DAYS = 1;
    }
}
