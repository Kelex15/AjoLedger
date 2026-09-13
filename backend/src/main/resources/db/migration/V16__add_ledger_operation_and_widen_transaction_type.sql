CREATE TABLE ledger_operation (
    id uuid NOT NULL PRIMARY KEY,
    operation_type varchar(40) NOT NULL,
    reference_id uuid,
    created_at timestamptz(6) NOT NULL DEFAULT now()
);

ALTER TABLE transaction ADD COLUMN operation_id uuid REFERENCES ledger_operation(id);

ALTER TABLE transaction DROP CONSTRAINT transaction_transaction_type_check;
ALTER TABLE transaction ADD CONSTRAINT transaction_transaction_type_check
    CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CONTRIBUTION', 'CONTRIBUTION_REFUND',
        'PAYOUT', 'PAYOUT_BUFFER_FEE', 'PAYOUT_LIEN_SKIM', 'LIEN_HOLD', 'LIEN_WITHDRAWAL', 'LIEN_SEIZURE',
        'BUFFER_PAYOUT_TO_COVER_DEFAULT', 'CIRCLE_RESERVE_DISBURSEMENT'));

ALTER TABLE circle_payout RENAME COLUMN transaction_id TO operation_id;
