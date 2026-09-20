CREATE TABLE IF NOT EXISTS inventory_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL UNIQUE COLLATE NOCASE,
  unit TEXT NOT NULL DEFAULT 'عدد',
  stock_qty INTEGER NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
  min_stock INTEGER NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
  purchase_price INTEGER NOT NULL DEFAULT 0 CHECK (purchase_price >= 0),
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inventory_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  item_id INTEGER NOT NULL,
  movement_type TEXT NOT NULL CHECK (
    movement_type IN (
      'opening','purchase','adjustment_in','adjustment_out',
      'sale','sale_reverse','waste'
    )
  ),
  qty_delta INTEGER NOT NULL,
  unit_cost INTEGER NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  order_id INTEGER,
  order_item_id INTEGER,
  note TEXT,
  created_by INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (item_id) REFERENCES inventory_items(id),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS catalog_inventory_links (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  catalog_type TEXT NOT NULL CHECK (catalog_type IN ('hookah','service')),
  catalog_id INTEGER NOT NULL,
  inventory_item_id INTEGER NOT NULL,
  qty_per_unit INTEGER NOT NULL CHECK (qty_per_unit > 0),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
  UNIQUE (catalog_type, catalog_id, inventory_item_id)
);

ALTER TABLE order_items ADD COLUMN catalog_id INTEGER;

CREATE INDEX IF NOT EXISTS idx_inventory_movements_item
ON inventory_movements(item_id, created_at);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_order
ON inventory_movements(order_id);

CREATE INDEX IF NOT EXISTS idx_catalog_inventory_links_catalog
ON catalog_inventory_links(catalog_type, catalog_id);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('cashier','manage_inventory',1);

INSERT OR IGNORE INTO role_permissions (role, permission, allowed)
VALUES ('staff','manage_inventory',0);
