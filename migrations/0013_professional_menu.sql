CREATE TABLE IF NOT EXISTS catalog_categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  section TEXT NOT NULL CHECK (section IN ('hookah','drink','food','service')),
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (section, name)
);

ALTER TABLE hookah_catalog ADD COLUMN category_id INTEGER;
ALTER TABLE hookah_catalog ADD COLUMN description TEXT;
ALTER TABLE hookah_catalog ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

ALTER TABLE service_catalog ADD COLUMN item_kind TEXT NOT NULL DEFAULT 'service'
CHECK (item_kind IN ('drink','food','service'));
ALTER TABLE service_catalog ADD COLUMN category_id INTEGER;
ALTER TABLE service_catalog ADD COLUMN description TEXT;
ALTER TABLE service_catalog ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

ALTER TABLE order_items ADD COLUMN catalog_kind TEXT
CHECK (catalog_kind IN ('hookah','drink','food','service'));

CREATE INDEX IF NOT EXISTS idx_catalog_categories_section_sort
ON catalog_categories(section, active, sort_order, name);

CREATE INDEX IF NOT EXISTS idx_hookah_catalog_category_sort
ON hookah_catalog(category_id, active, sort_order, name);

CREATE INDEX IF NOT EXISTS idx_service_catalog_kind_category_sort
ON service_catalog(item_kind, category_id, active, sort_order, name);

UPDATE order_items
SET catalog_kind = CASE
  WHEN item_type='hookah' THEN 'hookah'
  ELSE 'service'
END
WHERE catalog_kind IS NULL;

INSERT OR IGNORE INTO catalog_categories(section,name,sort_order,active)
VALUES
  ('hookah','طعم‌های قلیان',10,1),
  ('drink','نوشیدنی گرم',10,1),
  ('drink','نوشیدنی سرد',20,1),
  ('food','خوراکی و میان‌وعده',10,1),
  ('service','خدمات',10,1);

UPDATE hookah_catalog
SET category_id=(
  SELECT id FROM catalog_categories
  WHERE section='hookah' AND name='طعم‌های قلیان'
)
WHERE category_id IS NULL;

UPDATE service_catalog
SET category_id=(
  SELECT id FROM catalog_categories
  WHERE section='service' AND name='خدمات'
)
WHERE category_id IS NULL;
