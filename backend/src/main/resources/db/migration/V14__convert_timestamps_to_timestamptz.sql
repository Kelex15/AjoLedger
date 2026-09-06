ALTER TABLE users
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamptz(6) USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE user_auth
    ALTER COLUMN verification_token_expires_at TYPE timestamptz(6) USING verification_token_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_failed_login_at TYPE timestamptz(6) USING last_failed_login_at AT TIME ZONE 'UTC',
    ALTER COLUMN locked_until TYPE timestamptz(6) USING locked_until AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamptz(6) USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE refresh_token
    ALTER COLUMN expires_at TYPE timestamptz(6) USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN issued_at TYPE timestamptz(6) USING issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN revoked_at TYPE timestamptz(6) USING revoked_at AT TIME ZONE 'UTC';

ALTER TABLE audit_log
    ALTER COLUMN occurred_at TYPE timestamptz(6) USING occurred_at AT TIME ZONE 'UTC';

ALTER TABLE circle
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamptz(6) USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_round_at TYPE timestamptz(6) USING last_round_at AT TIME ZONE 'UTC',
    ALTER COLUMN enrollment_deadline TYPE timestamptz(6) USING enrollment_deadline AT TIME ZONE 'UTC';

ALTER TABLE circle_membership
    ALTER COLUMN joined_at TYPE timestamptz(6) USING joined_at AT TIME ZONE 'UTC';

ALTER TABLE circle_payout
    ALTER COLUMN paid_at TYPE timestamptz(6) USING paid_at AT TIME ZONE 'UTC';

ALTER TABLE circle_invite
    ALTER COLUMN redeemed_at TYPE timestamptz(6) USING redeemed_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC';

ALTER TABLE account
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE timestamptz(6) USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE transaction
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC';

ALTER TABLE ledger_entry
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC';

ALTER TABLE idempotency_key
    ALTER COLUMN created_at TYPE timestamptz(6) USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE timestamptz(6) USING completed_at AT TIME ZONE 'UTC';
