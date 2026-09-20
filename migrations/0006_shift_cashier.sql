CREATE TABLE IF NOT EXISTS cash_shifts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed')),
  opening_cash INTEGER NOT NULL DEFAULT 0 CHECK (opening_cash >= 0),
  expected_cash INTEGER,
  counted_cash INTEGER,
  cash_difference INTEGER,
  closing_note TEXT,
  opened_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_open_shift_per_user
ON cash_shifts(user_id) WHERE status='open';

CREATE INDEX IF NOT EXISTS idx_cash_shifts_user_opened
ON cash_shifts(user_id, opened_at);

ALTER TABLE orders ADD COLUMN opened_shift_id INTEGER REFERENCES cash_shifts(id);
ALTER TABLE orders ADD COLUMN settled_shift_id INTEGER REFERENCES cash_shifts(id);

ALTER TABLE payments ADD COLUMN shift_id INTEGER REFERENCES cash_shifts(id);
CREATE INDEX IF NOT EXISTS idx_payments_shift ON payments(shift_id);

ALTER TABLE expenses ADD COLUMN shift_id INTEGER REFERENCES cash_shifts(id);
ALTER TABLE expenses ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'cash'
CHECK (payment_method IN ('cash','card','transfer'));
CREATE INDEX IF NOT EXISTS idx_expenses_shift ON expenses(shift_id);
