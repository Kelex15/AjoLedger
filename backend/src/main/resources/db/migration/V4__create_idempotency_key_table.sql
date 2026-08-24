CREATE TABLE idempotency_key (
    id uuid NOT NULL PRIMARY KEY,
    idempotency_key varchar(255) NOT NULL UNIQUE,
    request_type varchar(40) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED')),
    transaction_id uuid REFERENCES transaction(id),
    failure_reason text,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    completed_at timestamp(6)
);
