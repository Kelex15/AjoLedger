-- V28's synchronous per-insert trigger serialized every ledger write on one shared lock.
-- Measured: throughput plateaued around 500-1200 tps regardless of concurrency, versus
-- 9700+ tps with no trigger at all. Replaced with async batch sealing instead: writes stay
-- untouched, a scheduled job seals accumulated rows into a separate append-only chain table.
DROP TRIGGER trg_ledger_operation_chain_hash ON ledger_operation;
DROP TRIGGER trg_transaction_chain_hash ON transaction;
DROP TRIGGER trg_ledger_entry_chain_hash ON ledger_entry;
DROP FUNCTION compute_ledger_row_hash();
DROP TABLE ledger_hash_chain;

ALTER TABLE ledger_operation DROP COLUMN chain_seq;
ALTER TABLE ledger_operation DROP COLUMN row_hash;
ALTER TABLE transaction DROP COLUMN chain_seq;
ALTER TABLE transaction DROP COLUMN row_hash;
ALTER TABLE ledger_entry DROP COLUMN chain_seq;
ALTER TABLE ledger_entry DROP COLUMN row_hash;

-- A deterministic tiebreaker for the sealing job's own ordering, nothing more. created_at alone
-- isn't enough since sibling legs of the same transaction share the exact same timestamp
-- (LedgerService.insertLegs passes one Instant to both).
ALTER TABLE ledger_operation ADD COLUMN insert_seq bigserial;
ALTER TABLE transaction ADD COLUMN insert_seq bigserial;
ALTER TABLE ledger_entry ADD COLUMN insert_seq bigserial;

CREATE TABLE ledger_hash_chain_entry (
    id uuid NOT NULL PRIMARY KEY,
    seq bigint NOT NULL UNIQUE,
    source_table varchar(20) NOT NULL CHECK (source_table IN ('ledger_operation', 'transaction', 'ledger_entry')),
    source_id uuid NOT NULL,
    source_seq bigint NOT NULL,
    prev_hash char(64) NOT NULL,
    row_hash char(64) NOT NULL,
    sealed_at timestamptz(6) NOT NULL DEFAULT now(),
    UNIQUE (source_table, source_id)
);

CREATE TRIGGER trg_ledger_hash_chain_entry_append_only
BEFORE UPDATE OR DELETE ON ledger_hash_chain_entry
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
