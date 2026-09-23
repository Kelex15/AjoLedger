CREATE TRIGGER trg_reconciliation_mismatch_append_only
BEFORE UPDATE OR DELETE ON reconciliation_mismatch
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE FUNCTION reject_webhook_event_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'processed_webhook_event is append-only: DELETE not allowed';
    END IF;

    IF OLD.processed_at IS NOT NULL THEN
        RAISE EXCEPTION 'processed_webhook_event row % is already processed, no further updates allowed', OLD.id;
    END IF;

    IF NEW.id <> OLD.id OR NEW.processor <> OLD.processor OR NEW.event_id <> OLD.event_id
        OR NEW.raw_payload <> OLD.raw_payload OR NEW.received_at <> OLD.received_at THEN
        RAISE EXCEPTION 'processed_webhook_event: only processed_at and ledger_transaction_id may change after receipt';
    END IF;

    IF NEW.processed_at IS NULL THEN
        RAISE EXCEPTION 'processed_webhook_event: processed_at cannot be cleared';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_webhook_event_guarded_update
BEFORE UPDATE OR DELETE ON processed_webhook_event
FOR EACH ROW EXECUTE FUNCTION reject_webhook_event_mutation();
