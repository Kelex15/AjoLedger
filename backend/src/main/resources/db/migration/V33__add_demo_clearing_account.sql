-- Source of the money the demo seeder deposits, kept apart from the processor clearing
-- accounts so their balances still match what Stripe and PayPal actually settled.
ALTER TABLE user_account DROP CONSTRAINT user_account_user_account_type_check;
ALTER TABLE user_account ADD CONSTRAINT user_account_user_account_type_check
    CHECK (user_account_type IN (
        'CHECKING', 'SAVINGS', 'LIEN', 'SYSTEM_BUFFER', 'CLEARING_STRIPE', 'CLEARING_PAYPAL', 'CLEARING_DEMO'
    ));

INSERT INTO account (id, owner_type, account_number, currency, balance, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000005', 'USER', 'SYS-DEMO-0001', 'USD', 0, now(), now());

INSERT INTO user_account (id, user_id, user_account_type)
VALUES ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'CLEARING_DEMO');
