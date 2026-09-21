CREATE TABLE circle_member_debit_authorization (
    id uuid NOT NULL PRIMARY KEY,
    circle_membership_id uuid NOT NULL UNIQUE REFERENCES circle_membership(id),
    remaining_ceiling numeric(38,2) NOT NULL CHECK (remaining_ceiling >= 0),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6)
);
