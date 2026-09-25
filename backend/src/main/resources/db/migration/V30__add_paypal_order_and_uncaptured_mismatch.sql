CREATE TABLE paypal_order (
    id uuid NOT NULL PRIMARY KEY,
    order_id varchar(255) NOT NULL UNIQUE,
    user_id uuid NOT NULL REFERENCES users(id),
    idempotency_key varchar(255) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency varchar(3) NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT now()
);
CREATE INDEX idx_paypal_order_created_at ON paypal_order(created_at);

CREATE TRIGGER trg_paypal_order_append_only
BEFORE UPDATE OR DELETE ON paypal_order
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

ALTER TABLE reconciliation_mismatch DROP CONSTRAINT reconciliation_mismatch_mismatch_type_check;
ALTER TABLE reconciliation_mismatch ADD CONSTRAINT reconciliation_mismatch_mismatch_type_check
    CHECK (mismatch_type IN (
        'MISSING_LEDGER_ENTRY', 'MISSING_PROCESSOR_RECORD', 'AMOUNT_MISMATCH', 'UNCAPTURED_ORDER'
    ));
