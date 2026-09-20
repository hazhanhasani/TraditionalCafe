ALTER TABLE customer_ledger ADD COLUMN shift_id INTEGER REFERENCES cash_shifts(id);
ALTER TABLE customer_ledger ADD COLUMN payment_method TEXT
CHECK (payment_method IS NULL OR payment_method IN ('cash','card','transfer'));

CREATE INDEX IF NOT EXISTS idx_customer_ledger_shift
ON customer_ledger(shift_id);
