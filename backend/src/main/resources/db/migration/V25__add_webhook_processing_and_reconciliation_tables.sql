CREATE TABLE processed_webhook_event (
    id uuid NOT NULL PRIMARY KEY,
    processor varchar(20) NOT NULL,
    event_id varchar(255) NOT NULL,
    raw_payload text NOT NULL,
    received_at timestamptz(6) NOT NULL DEFAULT now(),
    processed_at timestamptz(6),
    ledger_transaction_id uuid REFERENCES transaction(id),
    UNIQUE (processor, event_id)
);

CREATE TABLE reconciliation_mismatch (
    id uuid NOT NULL PRIMARY KEY,
    processor varchar(20) NOT NULL,
    event_id varchar(255),
    mismatch_type varchar(40) NOT NULL CHECK (mismatch_type IN (
        'MISSING_LEDGER_ENTRY', 'MISSING_PROCESSOR_RECORD', 'AMOUNT_MISMATCH'
    )),
    expected_amount numeric(19,4),
    actual_amount numeric(19,4),
    detected_at timestamptz(6) NOT NULL DEFAULT now()
);
CREATE INDEX idx_reconciliation_mismatch_detected_at ON reconciliation_mismatch(detected_at);
