INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('cashier','adjust_customer_ledger',1);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('staff','adjust_customer_ledger',0);
