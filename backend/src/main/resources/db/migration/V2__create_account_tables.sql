-- Account hierarchy: JOINED-table inheritance. `account` holds the shared columns;
-- `user_account`/`circle_account` each add their own columns and share the parent's PK.

CREATE TABLE account (
    id uuid NOT NULL PRIMARY KEY,
    owner_type varchar(31) NOT NULL,
    account_number varchar(20) NOT NULL UNIQUE,
    currency varchar(10) NOT NULL CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD')),
    balance numeric(19,4) NOT NULL,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6)
);

CREATE TABLE user_account (
    id uuid NOT NULL PRIMARY KEY REFERENCES account(id),
    user_id uuid NOT NULL REFERENCES users(id),
    user_account_type varchar(20) NOT NULL CHECK (user_account_type IN (
        'CHECKING', 'SAVINGS', 'LIEN', 'SYSTEM_BUFFER', 'CLEARING_STRIPE', 'CLEARING_PAYPAL'
    )),
    UNIQUE (user_id, user_account_type)
);

CREATE TABLE circle_account (
    id uuid NOT NULL PRIMARY KEY REFERENCES account(id),
    circle_id uuid NOT NULL REFERENCES circle(id),
    UNIQUE (circle_id)
);
