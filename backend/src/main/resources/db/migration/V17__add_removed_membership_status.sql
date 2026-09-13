ALTER TABLE circle_membership DROP CONSTRAINT circle_membership_status_check;
ALTER TABLE circle_membership ADD CONSTRAINT circle_membership_status_check
    CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'DEFAULTED', 'DISSOLVED', 'COMPLETED', 'REMOVED'));
