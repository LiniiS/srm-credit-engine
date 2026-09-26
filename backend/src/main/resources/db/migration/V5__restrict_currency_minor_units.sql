ALTER TABLE currency DROP CONSTRAINT currency_minor_units_check;
ALTER TABLE currency
    ADD CONSTRAINT ck_currency_minor_units CHECK (minor_units BETWEEN 0 AND 6);
