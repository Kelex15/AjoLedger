CREATE TABLE transaction (
    id uuid NOT NULL PRIMARY KEY,
    amount numeric(19,4) NOT NULL,
    description varchar(255),
    transaction_type varchar(40) NOT NULL CHECK (transaction_type IN (
        'DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CONTRIBUTION', 'PAYOUT',
        'LIEN_HOLD', 'LIEN_WITHDRAWAL', 'LIEN_SEIZURE',
        'BUFFER_PAYOUT_TO_COVER_DEFAULT', 'GROUP_SHORTFALL_ABSORPTION'
    )),
    created_at timestamp(6) NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entry (
    id uuid NOT NULL PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id),
    debit_amount numeric(19,4) NOT NULL DEFAULT 0 CHECK (debit_amount >= 0),
    credit_amount numeric(19,4) NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    transaction_id uuid NOT NULL REFERENCES transaction(id),
    created_at timestamp(6) NOT NULL DEFAULT now(),
    CHECK ((debit_amount = 0) <> (credit_amount = 0))
);
CREATE INDEX idx_ledger_entry_account ON ledger_entry(account_id, created_at);
CREATE INDEX idx_ledger_entry_transaction ON ledger_entry(transaction_id);

-- Net-zero invariant: every transaction's legs must balance to zero and have at least one debit + one credit.
-- Deferred to commit (not transition tables - Postgres doesn't allow REFERENCING on CONSTRAINT TRIGGER)
-- so by the time this fires, every leg for the transaction is already visible to this query,
-- regardless of how many separate INSERT statements wrote them.
CREATE FUNCTION check_transaction_balances() RETURNS trigger AS $$
DECLARE
    net numeric;
    leg_count int;
BEGIN
    SELECT SUM(debit_amount - credit_amount), COUNT(*)
    INTO net, leg_count
    FROM ledger_entry
    WHERE transaction_id = NEW.transaction_id;

    IF net <> 0 OR leg_count < 2 THEN
        RAISE EXCEPTION 'Unbalanced or malformed ledger transaction %', NEW.transaction_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_check_transaction_balance
AFTER INSERT ON ledger_entry
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION check_transaction_balances();

-- Append-only: transaction and ledger_entry rows are never modified or deleted once written.
CREATE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION '% is append-only: % not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transaction_append_only
BEFORE UPDATE OR DELETE ON transaction
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_entry_append_only
BEFORE UPDATE OR DELETE ON ledger_entry
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
