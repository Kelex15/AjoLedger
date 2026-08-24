-- Dedicated system user: owns the platform-wide singleton accounts below.
-- Never created through a public endpoint, never returned from user-facing account listings.
INSERT INTO users (id, username, first_name, last_name, email, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'system', 'System', 'Account', 'system@ajoledger.internal', now(), now());

INSERT INTO account (id, owner_type, account_number, currency, balance, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000002', 'USER', 'SYS-BUFFER-0001', 'USD', 0, now(), now()),
    ('00000000-0000-0000-0000-000000000003', 'USER', 'SYS-STRIPE-0001', 'USD', 0, now(), now()),
    ('00000000-0000-0000-0000-000000000004', 'USER', 'SYS-PAYPAL-0001', 'USD', 0, now(), now());

INSERT INTO user_account (id, user_id, user_account_type) VALUES
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'SYSTEM_BUFFER'),
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'CLEARING_STRIPE'),
    ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'CLEARING_PAYPAL');
