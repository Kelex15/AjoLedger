CREATE TABLE email_outbox (
    id uuid NOT NULL PRIMARY KEY,
    recipient varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    body text NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz(6) NOT NULL,
    expires_at timestamptz(6),
    last_error text,
    created_at timestamptz(6) NOT NULL DEFAULT now(),
    sent_at timestamptz(6)
);

-- The worker only ever reads pending rows, which stay a small slice of the table.
CREATE INDEX idx_email_outbox_pending ON email_outbox(next_attempt_at) WHERE status = 'PENDING';
