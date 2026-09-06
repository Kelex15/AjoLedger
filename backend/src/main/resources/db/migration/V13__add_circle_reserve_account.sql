ALTER TABLE circle_account ADD COLUMN circle_account_type varchar(20);
UPDATE circle_account SET circle_account_type = 'POT' WHERE circle_account_type IS NULL;
ALTER TABLE circle_account ALTER COLUMN circle_account_type SET NOT NULL;
ALTER TABLE circle_account ADD CONSTRAINT circle_account_circle_account_type_check
    CHECK (circle_account_type IN ('POT', 'RESERVE'));

ALTER TABLE circle_account DROP CONSTRAINT circle_account_circle_id_key;
ALTER TABLE circle_account ADD CONSTRAINT circle_account_circle_id_circle_account_type_key
    UNIQUE (circle_id, circle_account_type);

-- Pre-existing circles only ever got a POT account. Give each one a matching RESERVE account,
-- same id shared between account (base) and circle_account (subclass) per the JOINED-inheritance
-- mapping. MATERIALIZED forces the id to be computed once and reused, not regenerated per reference.
WITH new_accounts AS MATERIALIZED (
    SELECT gen_random_uuid() AS id, circle_id
    FROM circle_account
    WHERE circle_account_type = 'POT'
),
inserted_account AS (
    INSERT INTO account (id, owner_type, account_number, currency, balance, created_at, updated_at)
    SELECT id, 'CIRCLE', 'RESV' || substr(replace(id::text, '-', ''), 1, 16), 'USD', 0, now(), now()
    FROM new_accounts
)
INSERT INTO circle_account (id, circle_id, circle_account_type)
SELECT id, circle_id, 'RESERVE' FROM new_accounts;

ALTER TABLE transaction DROP CONSTRAINT transaction_transaction_type_check;
ALTER TABLE transaction ADD CONSTRAINT transaction_transaction_type_check
    CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CONTRIBUTION', 'CONTRIBUTION_REFUND',
        'PAYOUT', 'LIEN_HOLD', 'LIEN_WITHDRAWAL', 'LIEN_SEIZURE', 'BUFFER_PAYOUT_TO_COVER_DEFAULT',
        'CIRCLE_RESERVE_DISBURSEMENT'));
