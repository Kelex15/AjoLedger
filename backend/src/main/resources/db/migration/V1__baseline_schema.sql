-- Baseline: schema for the domains already stable before Flyway was introduced
-- (auth, user, circle). Captured from the ddl-auto-generated dev schema, hand-cleaned.

CREATE TABLE users (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamp(6) NOT NULL,
    email varchar(255) NOT NULL UNIQUE,
    first_name varchar(255) NOT NULL,
    last_name varchar(255) NOT NULL,
    updated_at timestamp(6),
    username varchar(255) NOT NULL UNIQUE
);

CREATE TABLE user_auth (
    id uuid NOT NULL PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users(id),
    password_hash varchar(255) NOT NULL,
    google_id varchar(255) UNIQUE,
    email_verified boolean NOT NULL,
    verification_token varchar(255),
    verification_token_expires_at timestamp(6),
    failed_login_attempts integer NOT NULL,
    last_failed_login_at timestamp(6),
    locked_until timestamp(6),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6)
);

CREATE TABLE refresh_token (
    id uuid NOT NULL PRIMARY KEY,
    expires_at timestamp(6) NOT NULL,
    family_id uuid NOT NULL,
    issued_at timestamp(6) NOT NULL,
    replaced_by_token_id uuid,
    revoked boolean NOT NULL,
    revoked_at timestamp(6),
    token_hash varchar(255) NOT NULL UNIQUE,
    user_id uuid NOT NULL REFERENCES users(id)
);
CREATE INDEX idx_refresh_token_family_id ON refresh_token(family_id);
CREATE INDEX idx_refresh_token_token_hash ON refresh_token(token_hash);

CREATE TABLE audit_log (
    id uuid NOT NULL PRIMARY KEY,
    detail varchar(255),
    event_type varchar(255) NOT NULL CHECK (event_type IN (
        'SIGNUP', 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED',
        'EMAIL_VERIFIED', 'GOOGLE_LINKED', 'LOGOUT', 'REFRESH_TOKEN_REUSE_DETECTED'
    )),
    ip_address varchar(255),
    occurred_at timestamp(6) NOT NULL,
    user_id uuid REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE circle (
    id uuid NOT NULL PRIMARY KEY,
    contribution_amount numeric(38,2) NOT NULL,
    created_at timestamp(6) NOT NULL,
    current_round integer NOT NULL,
    max_members integer NOT NULL,
    name varchar(255) NOT NULL,
    description varchar(255) NOT NULL,
    sizing_policy_type varchar(255) NOT NULL CHECK (sizing_policy_type = 'FIXED_SIZE'),
    status varchar(255) NOT NULL CHECK (status IN ('FORMING', 'ACTIVE', 'COMPLETED')),
    term integer NOT NULL,
    updated_at timestamp(6),
    created_by_user_id uuid NOT NULL REFERENCES users(id)
);

CREATE TABLE circle_membership (
    id uuid NOT NULL PRIMARY KEY,
    joined_at timestamp(6) NOT NULL,
    "position" integer,
    status varchar(255) NOT NULL CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    circle_id uuid NOT NULL REFERENCES circle(id),
    user_id uuid NOT NULL REFERENCES users(id),
    UNIQUE (circle_id, user_id)
);

CREATE TABLE circle_payout (
    id uuid NOT NULL PRIMARY KEY,
    paid_at timestamp(6) NOT NULL,
    round_number integer NOT NULL,
    circle_id uuid NOT NULL REFERENCES circle(id),
    recipient_user_id uuid NOT NULL REFERENCES users(id),
    UNIQUE (circle_id, round_number)
);
