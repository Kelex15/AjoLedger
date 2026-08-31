ALTER TABLE circle ADD COLUMN gated boolean NOT NULL DEFAULT false;

CREATE TABLE circle_invite (
    id uuid NOT NULL PRIMARY KEY,
    circle_id uuid NOT NULL REFERENCES circle(id),
    invited_by_user_id uuid NOT NULL REFERENCES users(id),
    code varchar(64) NOT NULL UNIQUE,
    invited_user_id uuid REFERENCES users(id),
    invited_email varchar(100),
    redeemed_at timestamp(6),
    created_at timestamp(6) NOT NULL,
    CHECK (invited_user_id IS NOT NULL OR invited_email IS NOT NULL)
);

CREATE INDEX idx_circle_invite_circle ON circle_invite(circle_id);
