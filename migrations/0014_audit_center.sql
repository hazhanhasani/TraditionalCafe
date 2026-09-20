CREATE INDEX IF NOT EXISTS idx_audit_logs_created
ON audit_logs(created_at, id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id
ON audit_logs(user_id, id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action
ON audit_logs(action, id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
ON audit_logs(entity_type, entity_id);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('cashier','view_audit_log',0);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('staff','view_audit_log',0);
