CREATE TRIGGER trg_ledger_operation_append_only
BEFORE UPDATE OR DELETE ON ledger_operation
FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
