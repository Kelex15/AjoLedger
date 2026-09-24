CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE ledger_hash_chain (
    id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    seq bigint NOT NULL,
    hash char(64) NOT NULL
);

INSERT INTO ledger_hash_chain (seq, hash) VALUES (0, repeat('0', 64));

ALTER TABLE ledger_operation ADD COLUMN chain_seq bigint;
ALTER TABLE ledger_operation ADD COLUMN row_hash char(64);
ALTER TABLE transaction ADD COLUMN chain_seq bigint;
ALTER TABLE transaction ADD COLUMN row_hash char(64);
ALTER TABLE ledger_entry ADD COLUMN chain_seq bigint;
ALTER TABLE ledger_entry ADD COLUMN row_hash char(64);

CREATE UNIQUE INDEX idx_ledger_operation_chain_seq ON ledger_operation(chain_seq);
CREATE UNIQUE INDEX idx_transaction_chain_seq ON transaction(chain_seq);
CREATE UNIQUE INDEX idx_ledger_entry_chain_seq ON ledger_entry(chain_seq);

-- One global chain across ledger_operation/transaction/ledger_entry, in true commit order.
-- Locks the singleton ledger_hash_chain row FOR UPDATE, so concurrent inserts across any of
-- the three tables serialize on this one row instead of racing on chain_seq/prev-hash.
-- Each row's hash covers the table it came from, its position in the chain, the previous
-- row's hash, and its own contents (to_jsonb(NEW), minus the two columns this trigger sets).
CREATE FUNCTION compute_ledger_row_hash() RETURNS trigger AS $$
DECLARE
    prev_seq bigint;
    prev_hash char(64);
    payload text;
BEGIN
    SELECT seq, hash INTO prev_seq, prev_hash FROM ledger_hash_chain WHERE id = 1 FOR UPDATE;

    NEW.chain_seq := prev_seq + 1;
    payload := TG_TABLE_NAME || '|' || NEW.chain_seq || '|' || prev_hash || '|'
        || (to_jsonb(NEW) - 'row_hash' - 'chain_seq')::text;
    NEW.row_hash := encode(digest(payload, 'sha256'), 'hex');

    UPDATE ledger_hash_chain SET seq = NEW.chain_seq, hash = NEW.row_hash WHERE id = 1;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_operation_chain_hash
BEFORE INSERT ON ledger_operation
FOR EACH ROW EXECUTE FUNCTION compute_ledger_row_hash();

CREATE TRIGGER trg_transaction_chain_hash
BEFORE INSERT ON transaction
FOR EACH ROW EXECUTE FUNCTION compute_ledger_row_hash();

CREATE TRIGGER trg_ledger_entry_chain_hash
BEFORE INSERT ON ledger_entry
FOR EACH ROW EXECUTE FUNCTION compute_ledger_row_hash();
