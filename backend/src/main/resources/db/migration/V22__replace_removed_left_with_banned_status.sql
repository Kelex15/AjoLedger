-- REMOVED/LEFT (pre-lock removal/self-leave) no longer persist a row at all - nothing depended
-- on them surviving (reputation scoring already ignored both, and the real financial trail lives
-- in the ledger regardless), and leaving no row behind is what lets a member rejoin afterward.
-- BANNED replaces them as the one pre-lock exit meant to persist, since blocking a future rejoin
-- is its entire purpose.
DELETE FROM circle_membership WHERE status IN ('REMOVED', 'LEFT');

ALTER TABLE circle_membership DROP CONSTRAINT circle_membership_status_check;
ALTER TABLE circle_membership ADD CONSTRAINT circle_membership_status_check
    CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'DEFAULTED', 'DISSOLVED', 'COMPLETED', 'BANNED'));
