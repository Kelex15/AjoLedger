ALTER TABLE circle_membership ADD COLUMN last_contributed_round integer;
ALTER TABLE circle ADD COLUMN last_round_at timestamp(6);
