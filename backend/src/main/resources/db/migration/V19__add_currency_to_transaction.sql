ALTER TABLE transaction
    ADD COLUMN currency varchar(10) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD'));
