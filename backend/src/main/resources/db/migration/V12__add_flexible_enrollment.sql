ALTER TABLE circle ADD COLUMN min_members integer;
ALTER TABLE circle ADD COLUMN enrollment_deadline timestamp(6);
ALTER TABLE circle ADD COLUMN locked_member_count integer;

ALTER TABLE circle DROP CONSTRAINT circle_status_check;
ALTER TABLE circle ADD CONSTRAINT circle_status_check
    CHECK (status IN ('FORMING', 'ACTIVE', 'COMPLETED', 'DISSOLVED'));

ALTER TABLE circle DROP CONSTRAINT circle_sizing_policy_type_check;
ALTER TABLE circle ADD CONSTRAINT circle_sizing_policy_type_check
    CHECK (sizing_policy_type IN ('FIXED_SIZE', 'OPEN_ENROLLMENT'));

ALTER TABLE circle_membership DROP CONSTRAINT circle_membership_status_check;
ALTER TABLE circle_membership ADD CONSTRAINT circle_membership_status_check
    CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'DEFAULTED', 'DISSOLVED'));

ALTER TABLE transaction DROP CONSTRAINT transaction_transaction_type_check;
ALTER TABLE transaction ADD CONSTRAINT transaction_transaction_type_check
    CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CONTRIBUTION', 'CONTRIBUTION_REFUND',
        'PAYOUT', 'LIEN_HOLD', 'LIEN_WITHDRAWAL', 'LIEN_SEIZURE', 'BUFFER_PAYOUT_TO_COVER_DEFAULT',
        'GROUP_SHORTFALL_ABSORPTION'));
