ALTER TABLE customers ADD COLUMN credit_limit INTEGER NOT NULL DEFAULT 0
CHECK (credit_limit >= 0);

ALTER TABLE customers ADD COLUMN due_days INTEGER NOT NULL DEFAULT 30
CHECK (due_days >= 0 AND due_days <= 3650);

ALTER TABLE customer_ledger ADD COLUMN due_at TEXT;

CREATE INDEX IF NOT EXISTS idx_customers_phone
ON customers(phone);

CREATE INDEX IF NOT EXISTS idx_customer_ledger_customer_created
ON customer_ledger(customer_id, created_at);

CREATE INDEX IF NOT EXISTS idx_customer_ledger_due
ON customer_ledger(customer_id, due_at);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('cashier','manage_customer_limits',1);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('staff','manage_customer_limits',0);
