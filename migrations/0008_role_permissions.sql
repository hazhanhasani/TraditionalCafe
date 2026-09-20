CREATE TABLE IF NOT EXISTS role_permissions (
  role TEXT NOT NULL CHECK (role IN ('admin','cashier','staff')),
  permission TEXT NOT NULL,
  allowed INTEGER NOT NULL DEFAULT 0 CHECK (allowed IN (0,1)),
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role, permission)
);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed) VALUES
('cashier','view_all_orders',1),
('cashier','manage_catalog',1),
('cashier','manage_expenses',1),
('cashier','view_reports',1),
('cashier','reverse_settlement',1),
('cashier','apply_discount',1),
('cashier','view_all_shifts',1);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed) VALUES
('staff','view_all_orders',0),
('staff','manage_catalog',0),
('staff','manage_expenses',0),
('staff','view_reports',0),
('staff','reverse_settlement',0),
('staff','apply_discount',0),
('staff','view_all_shifts',0);
