// stage7plus-failure-fix-validation-v2
// audit-center-e2e-trigger: v2
// full-debug-trigger: customer-ledger-complete-e2e
// setup-key-sync-trigger: configured
const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS",
  "access-control-allow-headers": "content-type,authorization,x-setup-key,x-offline-operation-id",
};

const encoder = new TextEncoder();
const PASSWORD_KDF_ITERATIONS = 5000;
const IRAN_TIME_ZONE = "Asia/Tehran";
const API_VERSION = "1.4.0";
const ROLE_PERMISSION_KEYS = [
  "view_all_orders",
  "manage_catalog",
  "manage_expenses",
  "view_reports",
  "reverse_settlement",
  "apply_discount",
  "view_all_shifts",
  "manage_inventory",
  "manage_customer_limits",
  "adjust_customer_ledger",
  "view_audit_log"
];

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function error(code, message, status = 400, extra = {}) {
  return json({ ok: false, error: code, message, ...extra }, status);
}

async function bodyJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

function intAmount(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return null;
  const i = Math.round(n);
  return i >= 0 ? i : null;
}

function iranTimeParts(date = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: IRAN_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const value = {};
  for (const part of parts) {
    if (part.type !== "literal") value[part.type] = part.value;
  }
  return value;
}

function iranDateKey(date = new Date()) {
  const p = iranTimeParts(date);
  return `${p.year}-${p.month}-${p.day}`;
}

function iranIsoLike(date = new Date()) {
  const p = iranTimeParts(date);
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}+03:30`;
}

function jalaliNowDisplay(date = new Date()) {
  return new Intl.DateTimeFormat("fa-IR-u-ca-persian", {
    timeZone: IRAN_TIME_ZONE,
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
}

function bytesToHex(buffer) {
  return [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(bytes = 32) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return bytesToHex(data);
}

async function sha256Hex(value) {
  return bytesToHex(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
}

async function hashPassword(password, saltHex) {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(String(password)),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const salt = new Uint8Array((saltHex.match(/.{1,2}/g) || []).map((x) => parseInt(x, 16)));
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: PASSWORD_KDF_ITERATIONS },
    key,
    256,
  );
  return bytesToHex(bits);
}

function bearer(request) {
  const header = request.headers.get("authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match ? match[1].trim() : "";
}

async function auth(request, env) {
  const token = bearer(request);
  if (!token) return null;
  const tokenHash = await sha256Hex(token);
  const row = await env.DB.prepare(
    `SELECT u.id, u.username, u.name, u.role, u.active
     FROM sessions s
     JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ? AND s.expires_at > CURRENT_TIMESTAMP AND u.active = 1
     LIMIT 1`,
  ).bind(tokenHash).first();
  return row || null;
}

function requireRole(user, roles) {
  return user && roles.includes(user.role);
}

function canManageOrder(user, order) {
  if (!user || !order) return false;
  return user.role !== "staff" || Number(order.opened_by) === Number(user.id);
}

async function rolePermissions(env, role) {
  const defaults = {};
  for (const key of ROLE_PERMISSION_KEYS) {
    defaults[key] = role === "admin" || role === "cashier";
  }
  if (role === "staff") {
    for (const key of ROLE_PERMISSION_KEYS) defaults[key] = false;
    return defaults;
  }
  if (role === "admin") return defaults;

  try {
    const rows = await env.DB.prepare(
      "SELECT permission, allowed FROM role_permissions WHERE role=?"
    ).bind(role).all();
    for (const row of rows.results || []) {
      if (ROLE_PERMISSION_KEYS.includes(row.permission)) {
        defaults[row.permission] = Number(row.allowed) === 1;
      }
    }
  } catch {}
  return defaults;
}

async function hasPermission(env, user, permission) {
  if (!user) return false;
  if (user.role === "admin") return true;
  if (user.role === "staff") return false;
  const permissions = await rolePermissions(env, user.role);
  return permissions[permission] === true;
}

async function refreshCatalogRecipeCost(env, catalogType, catalogId) {
  if (!["hookah","service"].includes(catalogType)) return 0;

  const recipe = await env.DB.prepare(
    `SELECT COALESCE(SUM(l.qty_per_unit * i.purchase_price),0) AS recipe_cost,
            COUNT(*) AS components
     FROM catalog_inventory_links l
     JOIN inventory_items i ON i.id=l.inventory_item_id
     WHERE l.catalog_type=? AND l.catalog_id=?`
  ).bind(catalogType, catalogId).first();

  const components = Number(recipe?.components || 0);
  const recipeCost = Number(recipe?.recipe_cost || 0);
  if (components > 0) {
    const tableName = catalogType === "hookah" ? "hookah_catalog" : "service_catalog";
    await env.DB.prepare(
      `UPDATE ${tableName}
       SET cost=?, updated_at=CURRENT_TIMESTAMP
       WHERE id=?`
    ).bind(recipeCost, catalogId).run();
  }
  return recipeCost;
}

async function refreshRecipesUsingInventory(env, inventoryItemId) {
  const links = await env.DB.prepare(
    `SELECT DISTINCT catalog_type, catalog_id
     FROM catalog_inventory_links
     WHERE inventory_item_id=?`
  ).bind(inventoryItemId).all();

  for (const row of links.results || []) {
    await refreshCatalogRecipeCost(env, row.catalog_type, Number(row.catalog_id));
  }
}

async function buildRecipe(env, catalogType, catalogId) {
  if (!["hookah","service"].includes(catalogType)) return null;
  const tableName = catalogType === "hookah" ? "hookah_catalog" : "service_catalog";

  const catalog = await env.DB.prepare(
    `SELECT id,name,price,cost,active FROM ${tableName} WHERE id=?`
  ).bind(catalogId).first();
  if (!catalog) return null;

  const rows = await env.DB.prepare(
    `SELECT l.id AS link_id, l.inventory_item_id, l.qty_per_unit,
            i.name AS inventory_name, i.unit, i.stock_qty, i.min_stock,
            i.purchase_price, i.active
     FROM catalog_inventory_links l
     JOIN inventory_items i ON i.id=l.inventory_item_id
     WHERE l.catalog_type=? AND l.catalog_id=?
     ORDER BY i.name`
  ).bind(catalogType, catalogId).all();

  let recipeCost = 0;
  let canMake = null;
  let lowComponents = 0;
  const ingredients = [];

  for (const row of rows.results || []) {
    const qty = Number(row.qty_per_unit || 0);
    const stock = Number(row.stock_qty || 0);
    const purchasePrice = Number(row.purchase_price || 0);
    const componentCost = qty * purchasePrice;
    const possible = qty > 0 ? Math.floor(stock / qty) : 0;

    recipeCost += componentCost;
    canMake = canMake === null ? possible : Math.min(canMake, possible);
    if (stock <= Number(row.min_stock || 0) || possible <= 0) lowComponents++;

    ingredients.push({
      link_id: Number(row.link_id),
      inventory_item_id: Number(row.inventory_item_id),
      inventory_name: row.inventory_name,
      unit: row.unit,
      qty_per_unit: qty,
      stock_qty: stock,
      min_stock: Number(row.min_stock || 0),
      purchase_price: purchasePrice,
      component_cost: componentCost,
      can_make: possible,
      active: Number(row.active || 0),
    });
  }

  return {
    catalog_type: catalogType,
    catalog_id: Number(catalog.id),
    name: catalog.name,
    price: Number(catalog.price || 0),
    stored_cost: Number(catalog.cost || 0),
    recipe_cost: recipeCost,
    profit_per_unit: Number(catalog.price || 0) - recipeCost,
    component_count: ingredients.length,
    can_make: canMake === null ? 0 : canMake,
    low_components: lowComponents,
    ingredients,
  };
}

function auditCategory(action) {
  const value = String(action || "");
  if ([
    "setup_user","login","create_user","update_user","update_role_permissions"
  ].includes(value)) return "security";

  if ([
    "open_order","add_order_item","update_order_item_qty","delete_order_item",
    "settle_order","reverse_settlement","cancel_order","merge_orders",
    "transfer_order","hard_delete_order","sync_offline_order"
  ].includes(value)) return "sales";

  if ([
    "create_backup","scheduled_backup","restore_backup","delete_backup"
  ].includes(value)) return "system";

  if ([
    "open_shift","close_shift","create_expense","customer_payment",
    "customer_ledger_adjustment"
  ].includes(value)) return "finance";

  if ([
    "create_inventory_item","update_inventory_item","inventory_movement",
    "create_inventory_link","delete_inventory_link","update_recipe"
  ].includes(value)) return "inventory";

  if ([
    "create_catalog_category","update_catalog_category","create_catalog_item",
    "update_catalog_item","create_hookah"
  ].includes(value)) return "catalog";

  if (["create_customer","update_customer"].includes(value)) return "customers";
  return "system";
}

function auditSeverity(action) {
  const value = String(action || "");
  if ([
    "hard_delete_order","reverse_settlement","update_role_permissions",
    "customer_ledger_adjustment","restore_backup"
  ].includes(value)) return "critical";

  if ([
    "cancel_order","update_user","delete_order_item","delete_inventory_link",
    "inventory_movement","transfer_order","merge_orders"
  ].includes(value)) return "attention";

  return "normal";
}

async function audit(env, userId, action, entityType = null, entityId = null, details = null) {
  try {
    await env.DB.prepare(
      "INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details) VALUES (?,?,?,?,?)",
    ).bind(
      userId || null,
      action,
      entityType,
      entityId || null,
      details ? JSON.stringify(details) : null,
    ).run();
  } catch {
    // Audit logging must never make a business action fail.
  }
}


const BACKUP_EXCLUDED_TABLES = new Set([
  "backup_snapshots",
  "backup_snapshot_chunks",
  "d1_migrations",
  "sessions",
  "offline_operations"
]);

function backupId() {
  return "bkp_" + Date.now().toString(36) + "_" + randomHex(6);
}

function validIdentifier(value) {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(String(value || ""));
}

function quoteIdentifier(value) {
  const name = String(value || "");
  if (!validIdentifier(name)) throw new Error("invalid_identifier");
  return '"' + name + '"';
}

async function backupTableNames(env) {
  const rows = await env.DB.prepare(
    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
  ).all();
  return (rows.results || [])
    .map((row) => String(row.name || ""))
    .filter((name) =>
      validIdentifier(name) &&
      !name.startsWith("_cf_") &&
      !BACKUP_EXCLUDED_TABLES.has(name)
    );
}

async function flushBackupChunk(env, snapshotId, tableName, chunkIndex, rows) {
  const payload = JSON.stringify(rows);
  const checksum = await sha256Hex(payload);
  await env.DB.prepare(
    "INSERT INTO backup_snapshot_chunks (snapshot_id,table_name,chunk_index,row_count,payload,checksum) VALUES (?,?,?,?,?,?)"
  ).bind(snapshotId, tableName, chunkIndex, rows.length, payload, checksum).run();
  return { bytes: encoder.encode(payload).byteLength, checksum };
}

async function createBackupSnapshot(env, userId, kind = "manual", reason = "", metadata = {}) {
  const allowedKinds = ["manual","daily","pre_delete","pre_restore"];
  if (!allowedKinds.includes(kind)) throw new Error("invalid_backup_kind");

  const id = backupId();
  await env.DB.prepare(
    "INSERT INTO backup_snapshots (id,kind,reason,status,created_by,metadata) VALUES (?,?,?,?,?,?)"
  ).bind(
    id,
    kind,
    String(reason || "").slice(0, 240) || null,
    "creating",
    userId || null,
    JSON.stringify(metadata || {})
  ).run();

  try {
    const tables = await backupTableNames(env);
    let rowCount = 0;
    let sizeBytes = 0;
    const digestParts = [];

    for (const table of tables) {
      const result = await env.DB.prepare(
        "SELECT * FROM " + quoteIdentifier(table)
      ).all();
      const rows = result.results || [];
      rowCount += rows.length;

      let chunk = [];
      let chunkIndex = 0;
      let approxBytes = 2;

      const flush = async () => {
        if (chunk.length === 0) return;
        const saved = await flushBackupChunk(
          env,
          id,
          table,
          chunkIndex++,
          chunk
        );
        sizeBytes += saved.bytes;
        digestParts.push(table + ":" + saved.checksum);
        chunk = [];
        approxBytes = 2;
      };

      for (const row of rows) {
        const encoded = JSON.stringify(row);
        const rowBytes = encoder.encode(encoded).byteLength + 1;
        if (chunk.length > 0 && (chunk.length >= 100 || approxBytes + rowBytes > 280000)) {
          await flush();
        }
        chunk.push(row);
        approxBytes += rowBytes;
      }

      await flush();

      if (rows.length === 0) {
        const saved = await flushBackupChunk(env, id, table, 0, []);
        sizeBytes += saved.bytes;
        digestParts.push(table + ":" + saved.checksum);
      }
    }

    const checksum = await sha256Hex(digestParts.join("|"));
    const finalMetadata = {
      ...(metadata || {}),
      schema_version: 1,
      tables
    };

    await env.DB.prepare(
      "UPDATE backup_snapshots SET status='ready',completed_at=CURRENT_TIMESTAMP,table_count=?,row_count=?,size_bytes=?,checksum=?,metadata=? WHERE id=?"
    ).bind(
      tables.length,
      rowCount,
      sizeBytes,
      checksum,
      JSON.stringify(finalMetadata),
      id
    ).run();

    return await env.DB.prepare(
      "SELECT * FROM backup_snapshots WHERE id=?"
    ).bind(id).first();
  } catch (e) {
    try {
      await env.DB.prepare(
        "UPDATE backup_snapshots SET status='failed',completed_at=CURRENT_TIMESTAMP,metadata=? WHERE id=?"
      ).bind(
        JSON.stringify({
          ...(metadata || {}),
          error: String(e && e.message ? e.message : e)
        }),
        id
      ).run();
    } catch {}
    throw e;
  }
}

async function pruneAutomaticBackups(env) {
  await env.DB.prepare(
    "DELETE FROM backup_snapshots WHERE kind IN ('daily','pre_delete','pre_restore') AND created_at < datetime('now','-7 days')"
  ).run();
}

async function loadBackupSnapshot(env, snapshotId) {
  const snapshot = await env.DB.prepare(
    "SELECT * FROM backup_snapshots WHERE id=?"
  ).bind(snapshotId).first();
  if (!snapshot || snapshot.status !== "ready") return null;

  const chunks = await env.DB.prepare(
    "SELECT table_name,chunk_index,payload,row_count,checksum FROM backup_snapshot_chunks WHERE snapshot_id=? ORDER BY table_name,chunk_index"
  ).bind(snapshotId).all();

  const tables = {};
  const digestParts = [];

  for (const chunk of chunks.results || []) {
    const tableName = String(chunk.table_name || "");
    if (!validIdentifier(tableName)) continue;

    const payload = String(chunk.payload || "[]");
    const actualChunkChecksum = await sha256Hex(payload);
    if (chunk.checksum && actualChunkChecksum !== String(chunk.checksum)) {
      const err = new Error("backup_chunk_checksum_mismatch");
      err.code = "backup_integrity_failed";
      err.table = tableName;
      err.chunk = Number(chunk.chunk_index || 0);
      throw err;
    }

    digestParts.push(tableName + ":" + actualChunkChecksum);

    if (!tables[tableName]) tables[tableName] = [];
    const rows = JSON.parse(payload);
    if (!Array.isArray(rows)) {
      const err = new Error("backup_chunk_payload_invalid");
      err.code = "backup_integrity_failed";
      err.table = tableName;
      err.chunk = Number(chunk.chunk_index || 0);
      throw err;
    }
    tables[tableName].push(...rows);
  }

  const actualSnapshotChecksum = await sha256Hex(digestParts.join("|"));
  if (snapshot.checksum && actualSnapshotChecksum !== String(snapshot.checksum)) {
    const err = new Error("backup_snapshot_checksum_mismatch");
    err.code = "backup_integrity_failed";
    throw err;
  }

  return { snapshot, tables };
}

async function tableRestoreOrder(env, tableNames) {
  const available = new Set(tableNames.filter(validIdentifier));
  const dependencies = new Map();

  for (const table of available) {
    const rows = await env.DB.prepare(
      "PRAGMA foreign_key_list(" + quoteIdentifier(table) + ")"
    ).all();
    const deps = new Set();
    for (const row of rows.results || []) {
      const parent = String(row.table || "");
      if (available.has(parent) && parent !== table) deps.add(parent);
    }
    dependencies.set(table, deps);
  }

  const remaining = new Set(available);
  const ordered = [];
  while (remaining.size > 0) {
    let progressed = false;
    for (const table of Array.from(remaining)) {
      const deps = dependencies.get(table) || new Set();
      const unresolved = Array.from(deps).some((dep) => remaining.has(dep));
      if (!unresolved) {
        ordered.push(table);
        remaining.delete(table);
        progressed = true;
      }
    }
    if (!progressed) {
      ordered.push(...Array.from(remaining).sort());
      break;
    }
  }
  return ordered;
}

async function executeStatementBatches(env, statements, size = 50) {
  for (let i = 0; i < statements.length; i += size) {
    await env.DB.batch(statements.slice(i, i + size));
  }
}

async function restoreBackupSnapshot(env, user, request, snapshotId) {
  const loaded = await loadBackupSnapshot(env, snapshotId);
  if (!loaded) {
    const err = new Error("backup_not_found");
    err.code = "backup_not_found";
    throw err;
  }

  const protectedTables = new Set([
    "users",
    "sessions",
    "role_permissions",
    "backup_snapshots",
    "backup_snapshot_chunks",
    "d1_migrations"
  ]);

  const restoreTables = Object.keys(loaded.tables)
    .filter((name) => validIdentifier(name) && !protectedTables.has(name));

  const safety = await createBackupSnapshot(
    env,
    user.id,
    "pre_restore",
    "before_restore:" + snapshotId,
    { source_backup_id: snapshotId }
  );

  const order = await tableRestoreOrder(env, restoreTables);

  const deletes = order
    .slice()
    .reverse()
    .map((table) => env.DB.prepare("DELETE FROM " + quoteIdentifier(table)));
  await executeStatementBatches(env, deletes, 40);

  for (const table of order) {
    const rows = loaded.tables[table] || [];
    const inserts = [];

    for (const row of rows) {
      if (!row || typeof row !== "object" || Array.isArray(row)) continue;
      const columns = Object.keys(row).filter(validIdentifier);
      if (columns.length === 0) continue;
      const sql =
        "INSERT INTO " + quoteIdentifier(table) +
        " (" + columns.map(quoteIdentifier).join(",") + ")" +
        " VALUES (" + columns.map(() => "?").join(",") + ")";
      inserts.push(
        env.DB.prepare(sql).bind(...columns.map((column) => row[column]))
      );
    }

    await executeStatementBatches(env, inserts, 40);
  }

  await audit(env, user.id, "restore_backup", "backup_snapshot", null, {
    backup_id: snapshotId,
    safety_backup_id: safety.id,
    restored_tables: restoreTables.length
  });

  return {
    backup_id: snapshotId,
    safety_backup_id: safety.id,
    restored_tables: restoreTables.length
  };
}

function csvEscape(value) {
  if (value === null || value === undefined) return "";
  const text = typeof value === "object" ? JSON.stringify(value) : String(value);
  return '"' + text.replace(/"/g, '""') + '"';
}

function backupCsv(tables, onlyTable = "") {
  if (onlyTable) {
    const rows = Array.isArray(tables[onlyTable]) ? tables[onlyTable] : [];
    const columns = [];
    const seen = new Set();
    for (const row of rows) {
      for (const key of Object.keys(row || {})) {
        if (validIdentifier(key) && !seen.has(key)) {
          seen.add(key);
          columns.push(key);
        }
      }
    }
    const lines = [columns.map(csvEscape).join(",")];
    for (const row of rows) {
      lines.push(columns.map((column) => csvEscape(row[column])).join(","));
    }
    return lines.join("\r\n");
  }

  const lines = ['"table","row_number","row_json"'];
  for (const table of Object.keys(tables).sort()) {
    const rows = Array.isArray(tables[table]) ? tables[table] : [];
    if (rows.length === 0) {
      lines.push([csvEscape(table), csvEscape(0), csvEscape({})].join(","));
      continue;
    }
    rows.forEach((row, index) => {
      lines.push([
        csvEscape(table),
        csvEscape(index + 1),
        csvEscape(row)
      ].join(","));
    });
  }
  return lines.join("\r\n");
}

function downloadResponse(body, contentType, filename) {
  const headers = {
    ...JSON_HEADERS,
    "content-type": contentType,
    "content-disposition": 'attachment; filename="' + filename + '"'
  };
  return new Response(body, { status: 200, headers });
}

function parseUtcMillis(value) {
  if (!value) return NaN;
  const raw = String(value).trim();
  if (!raw) return NaN;
  const normalized = /(?:Z|[+-]\d\d:?\d\d)$/.test(raw)
    ? raw
    : raw.replace(" ", "T") + "Z";
  return Date.parse(normalized);
}

function addDaysIso(value, days) {
  const start = parseUtcMillis(value);
  if (!Number.isFinite(start) || !days || days <= 0) return null;
  return new Date(start + Number(days) * 86400000).toISOString();
}

function buildCustomerAccountSnapshot(entries, dueDays) {
  const queue = [];
  let balance = 0;

  for (const entry of entries || []) {
    const amount = Number(entry.amount || 0);
    balance += amount;

    if (amount > 0) {
      queue.push({
        remaining: amount,
        created_at: entry.created_at || null,
        due_at: entry.due_at || addDaysIso(entry.created_at, dueDays),
      });
      continue;
    }

    if (amount < 0) {
      let credit = Math.abs(amount);
      while (credit > 0 && queue.length > 0) {
        const head = queue[0];
        const used = Math.min(credit, head.remaining);
        head.remaining -= used;
        credit -= used;
        if (head.remaining <= 0) queue.shift();
      }
    }
  }

  const now = Date.now();
  let overdueAmount = 0;
  let oldestUnpaidAt = null;
  let oldestDueAt = null;
  let maxDaysOverdue = 0;

  for (const debt of queue) {
    if (!oldestUnpaidAt) oldestUnpaidAt = debt.created_at || null;
    const dueMs = parseUtcMillis(debt.due_at);
    if (Number.isFinite(dueMs) && dueMs < now) {
      overdueAmount += Number(debt.remaining || 0);
      if (!oldestDueAt) oldestDueAt = debt.due_at;
      maxDaysOverdue = Math.max(
        maxDaysOverdue,
        Math.floor((now - dueMs) / 86400000)
      );
    }
  }

  return {
    balance,
    overdue_amount: overdueAmount,
    oldest_unpaid_at: oldestUnpaidAt,
    oldest_due_at: oldestDueAt,
    days_overdue: maxDaysOverdue,
  };
}

async function getOpenShift(env, userId) {
  return await env.DB.prepare(
    "SELECT * FROM cash_shifts WHERE user_id=? AND status='open' ORDER BY id DESC LIMIT 1"
  ).bind(userId).first();
}

async function buildShiftSummary(env, shift) {
  if (!shift) return null;

  const payments = await env.DB.prepare(
    `SELECT method, COALESCE(SUM(amount),0) AS amount
     FROM payments
     WHERE shift_id=?
     GROUP BY method`
  ).bind(shift.id).all();

  const expenses = await env.DB.prepare(
    `SELECT
       COALESCE(SUM(amount),0) AS total,
       COALESCE(SUM(CASE WHEN payment_method='cash' THEN amount ELSE 0 END),0) AS cash_amount
     FROM expenses
     WHERE shift_id=?`
  ).bind(shift.id).first();

  const collections = await env.DB.prepare(
    `SELECT payment_method, COALESCE(SUM(-amount),0) AS amount
     FROM customer_ledger
     WHERE shift_id=? AND entry_type='payment'
     GROUP BY payment_method`
  ).bind(shift.id).all();

  const orders = await env.DB.prepare(
    `SELECT COUNT(DISTINCT order_id) AS count
     FROM payments
     WHERE shift_id=?`
  ).bind(shift.id).first();

  const totals = { cash: 0, card: 0, transfer: 0, credit: 0 };
  for (const row of payments.results || []) {
    if (Object.prototype.hasOwnProperty.call(totals, row.method)) {
      totals[row.method] = Number(row.amount || 0);
    }
  }

  const collectionTotals = { cash: 0, card: 0, transfer: 0 };
  for (const row of collections.results || []) {
    if (Object.prototype.hasOwnProperty.call(collectionTotals, row.payment_method)) {
      collectionTotals[row.payment_method] = Number(row.amount || 0);
    }
  }

  const salesTotal = totals.cash + totals.card + totals.transfer + totals.credit;
  const collectionsTotal =
    collectionTotals.cash + collectionTotals.card + collectionTotals.transfer;
  const cashExpenses = Number(expenses?.cash_amount || 0);
  const expectedCash =
    Number(shift.opening_cash || 0) +
    totals.cash +
    collectionTotals.cash -
    cashExpenses;

  return {
    id: Number(shift.id),
    user_id: Number(shift.user_id),
    status: shift.status,
    opening_cash: Number(shift.opening_cash || 0),
    opened_at: shift.opened_at,
    closed_at: shift.closed_at,
    counted_cash: shift.counted_cash === null ? null : Number(shift.counted_cash),
    expected_cash: shift.expected_cash === null
      ? expectedCash
      : Number(shift.expected_cash),
    cash_difference: shift.cash_difference === null
      ? null
      : Number(shift.cash_difference),
    closing_note: shift.closing_note || "",
    sales_total: salesTotal,
    cash_sales: totals.cash,
    card_sales: totals.card,
    transfer_sales: totals.transfer,
    credit_sales: totals.credit,
    debt_collections_total: collectionsTotal,
    debt_collection_cash: collectionTotals.cash,
    debt_collection_card: collectionTotals.card,
    debt_collection_transfer: collectionTotals.transfer,
    expenses_total: Number(expenses?.total || 0),
    cash_expenses: cashExpenses,
    expected_cash_live: expectedCash,
    settled_orders: Number(orders?.count || 0),
  };
}

function validOfflineOperationId(value) {
  return /^[A-Za-z0-9._:-]{8,120}$/.test(String(value || ""));
}

async function routeWithOfflineIdempotency(request, env) {
  const method = String(request.method || "GET").toUpperCase();
  const operationId = String(
    request.headers.get("x-offline-operation-id") || ""
  ).trim();

  if (!operationId || !["POST","PATCH","DELETE"].includes(method)) {
    return await route(request, env);
  }

  if (!validOfflineOperationId(operationId)) {
    return error(
      "invalid_offline_operation_id",
      "شناسه عملیات آفلاین معتبر نیست.",
      400
    );
  }

  const user = await auth(request, env);
  if (!user) {
    return await route(request, env);
  }

  const url = new URL(request.url);
  const operationPath = url.pathname + url.search;

  const reserved = await env.DB.prepare(
    `INSERT OR IGNORE INTO offline_operations
     (user_id,operation_id,method,path,status)
     VALUES (?,?,?,?, 'processing')`
  ).bind(user.id, operationId, method, operationPath).run();

  if (Number(reserved?.meta?.changes || 0) === 0) {
    const previous = await env.DB.prepare(
      `SELECT status,response_status,response_body
       FROM offline_operations
       WHERE user_id=? AND operation_id=?`
    ).bind(user.id, operationId).first();

    if (previous && previous.status === "done") {
      const headers = {
        ...JSON_HEADERS,
        "x-offline-replayed": "1"
      };
      return new Response(
        previous.response_body || "{}",
        {
          status: Number(previous.response_status || 200),
          headers
        }
      );
    }

    return error(
      "offline_operation_processing",
      "این عملیات در حال همگام‌سازی است؛ دوباره تلاش کنید.",
      409
    );
  }

  try {
    const response = await route(request, env);

    if (response.status >= 200 && response.status < 300) {
      const body = await response.clone().text();
      await env.DB.prepare(
        `UPDATE offline_operations
         SET status='done',response_status=?,response_body=?,
             updated_at=CURRENT_TIMESTAMP
         WHERE user_id=? AND operation_id=?`
      ).bind(
        response.status,
        body,
        user.id,
        operationId
      ).run();

      await env.DB.prepare(
        `DELETE FROM offline_operations
         WHERE status='done'
           AND created_at < datetime('now','-30 days')`
      ).run();
    } else {
      await env.DB.prepare(
        "DELETE FROM offline_operations WHERE user_id=? AND operation_id=?"
      ).bind(user.id, operationId).run();
    }

    return response;
  } catch (e) {
    try {
      await env.DB.prepare(
        "DELETE FROM offline_operations WHERE user_id=? AND operation_id=?"
      ).bind(user.id, operationId).run();
    } catch {}
    throw e;
  }
}

async function recalcOrder(env, orderId) {
  const totals = await env.DB.prepare(
    "SELECT COALESCE(SUM(qty * unit_price),0) AS subtotal FROM order_items WHERE order_id = ?",
  ).bind(orderId).first();
  const order = await env.DB.prepare(
    "SELECT discount FROM orders WHERE id = ?",
  ).bind(orderId).first();
  const subtotal = Number(totals?.subtotal || 0);
  const discount = Math.max(0, Number(order?.discount || 0));
  const total = Math.max(0, subtotal - discount);
  await env.DB.prepare(
    "UPDATE orders SET subtotal = ?, total = ? WHERE id = ?",
  ).bind(subtotal, total, orderId).run();
  return { subtotal, discount, total };
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();

  if (method === "OPTIONS") return new Response(null, { status: 204, headers: JSON_HEADERS });

  if (!env.DB) {
    return error("db_not_bound", "D1 database binding is not configured.", 503);
  }

  if (path === "/" && method === "GET") {
    return json({
      ok: true,
      name: "TraditionalCafe API",
      version: API_VERSION,
      status: "ready",
      health: "/api/health",
      time: "/api/time"
    });
  }

  if (path === "/api/health" && method === "GET") {
    const db = await env.DB.prepare("SELECT 1 AS ok").first();
    const backupSchema = await env.DB.prepare(
      "SELECT COUNT(*) AS count FROM sqlite_master WHERE type='table' AND name IN ('backup_snapshots','backup_snapshot_chunks')"
    ).first();
    const offlineSchema = await env.DB.prepare(
      "SELECT COUNT(*) AS count FROM sqlite_master WHERE type='table' AND name='offline_operations'"
    ).first();
    return json({
      ok: true,
      service: "TraditionalCafe API",
      version: API_VERSION,
      database: db?.ok === 1 ? "ready" : "unknown",
      timezone: IRAN_TIME_ZONE,
      backup_recovery: Number(backupSchema?.count || 0) === 2 ? "ready" : "migration_pending",
      offline_sync: Number(offlineSchema?.count || 0) === 1 ? "ready" : "migration_pending",
    });
  }

  if (path === "/api/time" && method === "GET") {
    const now = new Date();
    return json({
      ok: true,
      timezone: IRAN_TIME_ZONE,
      utc: now.toISOString(),
      iran: iranIsoLike(now),
      iran_date: iranDateKey(now),
      jalali: jalaliNowDisplay(now),
    });
  }

  if (path === "/api/setup/status" && method === "GET") {
    const users = await env.DB.prepare("SELECT COUNT(*) AS count FROM users").first();
    const admins = await env.DB.prepare("SELECT COUNT(*) AS count FROM users WHERE role='admin' AND active=1").first();
    return json({
      ok: true,
      configured: Number(users?.count || 0) > 0,
      user_count: Number(users?.count || 0),
      admin_count: Number(admins?.count || 0),
    });
  }

  if (path === "/api/setup/crypto-check" && method === "POST") {
    if (!env.SETUP_KEY) return error("setup_key_missing", "SETUP_KEY Worker secret is not configured.", 503);
    const supplied = request.headers.get("x-setup-key") || "";
    if (supplied !== env.SETUP_KEY) return error("forbidden", "کلید راه‌اندازی معتبر نیست.", 403);

    try {
      const salt = randomHex(16);
      const hash = await hashPassword("TraditionalCafeCryptoCheck-2026!", salt);
      return json({
        ok: true,
        password_hashing: "ready",
        algorithm: "PBKDF2-SHA256",
        iterations: PASSWORD_KDF_ITERATIONS,
        hash_length: hash.length,
      });
    } catch (e) {
      console.error("password_hash_failed", e);
      return error("password_hash_failed", "سامانه رمزنگاری رمز عبور در Worker آماده نیست.", 503);
    }
  }

  if ((path === "/api/bootstrap" || path === "/api/setup/user") && method === "POST") {
    if (!env.SETUP_KEY) return error("setup_key_missing", "SETUP_KEY Worker secret is not configured.", 503);
    const supplied = request.headers.get("x-setup-key") || "";
    if (supplied !== env.SETUP_KEY) return error("forbidden", "کلید راه‌اندازی معتبر نیست.", 403);

    const data = await bodyJson(request);
    const username = String(data.username || "").trim().toLowerCase();
    const name = String(data.name || username).trim();
    const password = String(data.password || "");
    const role = ["admin","cashier","staff"].includes(data.role) ? data.role : "staff";

    if (!/^[a-z0-9._-]{3,32}$/.test(username)) {
      return error("invalid_username", "نام کاربری باید ۳ تا ۳۲ کاراکتر و شامل حروف انگلیسی، عدد، نقطه، خط تیره یا زیرخط باشد.");
    }
    if (name.length < 2 || name.length > 80) return error("invalid_name", "نام نمایشی معتبر نیست.");
    if (password.length < 8 || password.length > 128) {
      return error("invalid_password", "رمز عبور باید حداقل ۸ کاراکتر باشد.");
    }

    const salt = randomHex(16);
    let passwordHash;
    try {
      passwordHash = await hashPassword(password, salt);
    } catch (e) {
      console.error("setup_password_hash_failed", e);
      return error("password_hash_failed", "خطا در پردازش امن رمز عبور. دوباره تلاش کنید.", 503);
    }

    let userId;
    try {
      const result = await env.DB.prepare(
        "INSERT INTO users (username,name,role,pin_hash,pin_salt) VALUES (?,?,?,?,?)",
      ).bind(username, name, role, passwordHash, salt).run();
      userId = Number(result.meta.last_row_id);
    } catch (e) {
      const existing = await env.DB.prepare(
        "SELECT id FROM users WHERE username=? COLLATE NOCASE LIMIT 1"
      ).bind(username).first();

      if (existing) {
        return error("user_exists", "این نام کاربری قبلاً ثبت شده است.", 409);
      }

      console.error("setup_user_insert_failed", e);
      return error(
        "setup_user_insert_failed",
        "ساخت کاربر آزمایشی در پایگاه‌داده انجام نشد. دوباره تلاش کنید.",
        503
      );
    }

    const token = randomHex(32);
    const tokenHash = await sha256Hex(token);

    try {
      await env.DB.prepare(
        "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, datetime('now','+30 days'))",
      ).bind(userId, tokenHash).run();
    } catch (e) {
      try {
        await env.DB.prepare("DELETE FROM users WHERE id=?").bind(userId).run();
      } catch {}
      console.error("setup_session_insert_failed", e);
      return error(
        "setup_session_insert_failed",
        "ساخت نشست ورود انجام نشد. دوباره تلاش کنید.",
        503
      );
    }

    await audit(env, userId, "setup_user", "user", userId, { username, name, role });
    const permissions = await rolePermissions(env, role);
    return json({
      ok: true,
      token,
      user: { id: userId, username, name, role },
      permissions,
      expires_in_days: 30,
    }, 201);
  }

  if (path === "/api/auth/login" && method === "POST") {
    const data = await bodyJson(request);
    const username = String(data.username || "").trim().toLowerCase();
    const password = String(data.password || "");
    const user = await env.DB.prepare(
      "SELECT id, username, name, role, pin_hash, pin_salt, active FROM users WHERE username = ? COLLATE NOCASE LIMIT 1",
    ).bind(username).first();

    if (!user || Number(user.active) !== 1) {
      return error("invalid_credentials", "نام کاربری یا رمز عبور اشتباه است.", 401);
    }

    let candidate;
    try {
      candidate = await hashPassword(password, user.pin_salt);
    } catch (e) {
      console.error("login_password_hash_failed", e);
      return error("password_hash_failed", "خطا در پردازش امن رمز عبور. دوباره تلاش کنید.", 503);
    }
    if (candidate !== user.pin_hash) {
      return error("invalid_credentials", "نام کاربری یا رمز عبور اشتباه است.", 401);
    }

    const token = randomHex(32);
    const tokenHash = await sha256Hex(token);
    await env.DB.prepare(
      "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, datetime('now','+30 days'))",
    ).bind(user.id, tokenHash).run();
    await audit(env, user.id, "login", "user", user.id);

    const permissions = await rolePermissions(env, user.role);
    return json({
      ok: true,
      token,
      user: { id: user.id, username: user.username, name: user.name, role: user.role },
      permissions,
      expires_in_days: 30,
    });
  }

  const user = await auth(request, env);
  if (!user) return error("unauthorized", "Authentication required.", 401);

  if (path === "/api/auth/logout" && method === "POST") {
    const tokenHash = await sha256Hex(bearer(request));
    await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(tokenHash).run();
    return json({ ok: true });
  }

  if (path === "/api/me" && method === "GET") {
    return json({
      ok: true,
      user,
      permissions: await rolePermissions(env, user.role),
    });
  }

  if (path === "/api/offline/orders/sync" && method === "POST") {
    const data = await bodyJson(request);
    const operationId = String(
      request.headers.get("x-offline-operation-id") ||
      data.operation_id ||
      ""
    ).trim();
    const tableId = Number(data.table_id || 0);
    const rawItems = Array.isArray(data.items) ? data.items : [];

    if (!validOfflineOperationId(operationId)) {
      return error(
        "invalid_offline_operation_id",
        "شناسه سفارش آفلاین معتبر نیست."
      );
    }
    if (!tableId) {
      return error("invalid_table", "میز سفارش آفلاین معتبر نیست.");
    }
    if (rawItems.length === 0 || rawItems.length > 100) {
      return error(
        "invalid_offline_items",
        "سفارش آفلاین باید حداقل یک و حداکثر ۱۰۰ آیتم داشته باشد."
      );
    }

    const already = await env.DB.prepare(
      `SELECT id,table_id,subtotal,discount,total,status
       FROM orders
       WHERE offline_operation_id=?
       LIMIT 1`
    ).bind(operationId).first();

    if (already) {
      return json({
        ok:true,
        resumed:true,
        order_id:Number(already.id),
        table_id:Number(already.table_id),
        status:already.status,
        totals:{
          subtotal:Number(already.subtotal || 0),
          discount:Number(already.discount || 0),
          total:Number(already.total || 0)
        }
      });
    }

    const table = await env.DB.prepare(
      "SELECT id,name,active FROM cafe_tables WHERE id=?"
    ).bind(tableId).first();
    if (!table || Number(table.active) !== 1) {
      return error("table_not_found", "میز سفارش آفلاین پیدا نشد.", 404);
    }

    const occupied = await env.DB.prepare(
      `SELECT id,opened_by
       FROM orders
       WHERE table_id=? AND status='open'
       LIMIT 1`
    ).bind(tableId).first();

    if (occupied) {
      return error(
        "offline_table_conflict",
        "این میز بعد از قطع اینترنت دارای سفارش دیگری شده است؛ سفارش آفلاین نیاز به بررسی دارد.",
        409,
        { existing_order_id:Number(occupied.id) }
      );
    }

    const shift = await getOpenShift(env, user.id);
    if (!shift) {
      return error(
        "shift_required",
        "برای همگام‌سازی سفارش آفلاین باید شیفت کاربر باز باشد.",
        409
      );
    }

    const preparedItems = [];
    for (const raw of rawItems) {
      const catalogType = String(raw.catalog_type || "").toLowerCase();
      const catalogId = Number(raw.catalog_id || 0);
      const qty = Math.round(Number(raw.qty || 0));

      if (
        !["hookah","drink","food","service"].includes(catalogType) ||
        !Number.isFinite(qty) ||
        qty <= 0 ||
        qty > 999
      ) {
        return error(
          "invalid_offline_item",
          "یکی از آیتم‌های سفارش آفلاین معتبر نیست."
        );
      }

      if (catalogId > 0) {
        const item = catalogType === "hookah"
          ? await env.DB.prepare(
              "SELECT id,name,price,cost,active FROM hookah_catalog WHERE id=?"
            ).bind(catalogId).first()
          : await env.DB.prepare(
              `SELECT id,name,price,cost,active,item_kind
               FROM service_catalog
               WHERE id=? AND item_kind=?`
            ).bind(catalogId,catalogType).first();

        if (!item || Number(item.active) !== 1) {
          return error(
            "offline_catalog_item_changed",
            "یکی از اقلام سفارش آفلاین دیگر در منوی فعال موجود نیست.",
            409,
            { catalog_type:catalogType,catalog_id:catalogId }
          );
        }

        preparedItems.push({
          item_type:catalogType === "hookah" ? "hookah" : "service",
          catalog_type:catalogType,
          catalog_id:catalogId,
          name:item.name,
          qty,
          unit_price:Number(item.price || 0),
          unit_cost:Number(item.cost || 0)
        });
        continue;
      }

      if (user.role === "staff") {
        return error(
          "offline_catalog_required",
          "شاگرد در حالت آفلاین فقط می‌تواند از منوی تعریف‌شده فروش ثبت کند.",
          403
        );
      }

      const manualName = String(raw.name || "").trim().slice(0,100);
      const manualPrice = intAmount(raw.unit_price);
      if (!manualName || manualPrice === null) {
        return error(
          "invalid_offline_manual_item",
          "آیتم دستی سفارش آفلاین معتبر نیست."
        );
      }

      preparedItems.push({
        item_type:catalogType === "hookah" ? "hookah" : "service",
        catalog_type:catalogType,
        catalog_id:null,
        name:manualName,
        qty,
        unit_price:manualPrice,
        unit_cost:0
      });
    }

    let orderId = 0;
    try {
      const result = await env.DB.prepare(
        `INSERT INTO orders
         (table_id,opened_by,opened_shift_id,offline_operation_id,notes)
         VALUES (?,?,?,?,?)`
      ).bind(
        tableId,
        user.id,
        shift.id,
        operationId,
        data.note ? String(data.note).slice(0,500) : null
      ).run();

      orderId = Number(result.meta.last_row_id);

      const statements = preparedItems.map((item) =>
        env.DB.prepare(
          `INSERT INTO order_items
           (order_id,item_type,catalog_id,catalog_kind,name,qty,unit_price,unit_cost,created_by)
           VALUES (?,?,?,?,?,?,?,?,?)`
        ).bind(
          orderId,
          item.item_type,
          item.catalog_id,
          item.catalog_type,
          item.name,
          item.qty,
          item.unit_price,
          item.unit_cost,
          user.id
        )
      );

      if (statements.length) await env.DB.batch(statements);
      const totals = await recalcOrder(env, orderId);

      await audit(env,user.id,"sync_offline_order","order",orderId,{
        table_id:tableId,
        operation_id:operationId,
        item_count:preparedItems.length,
        offline_created_at:data.offline_created_at || null
      });

      return json({
        ok:true,
        synced:true,
        order_id:orderId,
        table_id:tableId,
        totals,
        warnings:[]
      },201);
    } catch (e) {
      if (orderId > 0) {
        try {
          await env.DB.prepare("DELETE FROM order_items WHERE order_id=?").bind(orderId).run();
          await env.DB.prepare("DELETE FROM orders WHERE id=?").bind(orderId).run();
        } catch {}
      }
      throw e;
    }
  }

  if (path === "/api/shifts/current" && method === "GET") {
    const shift = await getOpenShift(env, user.id);
    return json({
      ok: true,
      shift: shift ? await buildShiftSummary(env, shift) : null,
      timezone: IRAN_TIME_ZONE,
      jalali_now: jalaliNowDisplay(),
    });
  }

  if (path === "/api/shifts/open" && method === "POST") {
    const existing = await getOpenShift(env, user.id);
    if (existing) {
      return error("shift_already_open", "برای این کاربر یک شیفت باز وجود دارد.", 409, {
        shift_id: Number(existing.id),
      });
    }

    const data = await bodyJson(request);
    const openingCash = intAmount(data.opening_cash || 0);
    if (openingCash === null) {
      return error("invalid_opening_cash", "موجودی اولیه صندوق معتبر نیست.");
    }

    const result = await env.DB.prepare(
      "INSERT INTO cash_shifts (user_id, opening_cash) VALUES (?,?)"
    ).bind(user.id, openingCash).run();

    const shiftId = Number(result.meta.last_row_id);
    const shift = await env.DB.prepare(
      "SELECT * FROM cash_shifts WHERE id=?"
    ).bind(shiftId).first();

    await audit(env, user.id, "open_shift", "cash_shift", shiftId, {
      opening_cash: openingCash,
    });

    return json({ ok: true, shift: await buildShiftSummary(env, shift) }, 201);
  }

  if (path === "/api/shifts/my" && method === "GET") {
    const limit = Math.min(100, Math.max(1, Number(url.searchParams.get("limit") || 30)));
    const rows = await env.DB.prepare(
      `SELECT s.*, u.name AS user_name, u.username
       FROM cash_shifts s
       JOIN users u ON u.id=s.user_id
       WHERE s.user_id=?
       ORDER BY s.id DESC
       LIMIT ${limit}`
    ).bind(user.id).all();

    return json({ ok: true, scope: "self", shifts: rows.results || [] });
  }

  if (path === "/api/shifts" && method === "GET") {
    if (!(await hasPermission(env, user, "view_all_shifts"))) {
      return error("forbidden", "مجوز مشاهده شیفت‌های همه کاربران فعال نیست.", 403);
    }

    const limit = Math.min(200, Math.max(1, Number(url.searchParams.get("limit") || 100)));
    const rows = await env.DB.prepare(
      `SELECT s.*, u.name AS user_name, u.username
       FROM cash_shifts s
       JOIN users u ON u.id=s.user_id
       ORDER BY s.id DESC
       LIMIT ${limit}`
    ).all();

    return json({ ok: true, scope: "all", shifts: rows.results || [] });
  }

  const shiftDetail = path.match(/^\/api\/shifts\/(\d+)$/);
  if (shiftDetail && method === "GET") {
    const shiftId = Number(shiftDetail[1]);
    const shift = await env.DB.prepare(
      `SELECT s.*, u.name AS user_name, u.username
       FROM cash_shifts s
       JOIN users u ON u.id=s.user_id
       WHERE s.id=?`
    ).bind(shiftId).first();

    if (!shift) return error("not_found", "شیفت پیدا نشد.", 404);
    if (user.role === "staff" && Number(shift.user_id) !== Number(user.id)) {
      return error("forbidden", "شاگرد فقط می‌تواند شیفت خودش را مشاهده کند.", 403);
    }

    const summary = await buildShiftSummary(env, shift);
    summary.user_name = shift.user_name;
    summary.username = shift.username;
    return json({ ok: true, shift: summary });
  }

  const closeShift = path.match(/^\/api\/shifts\/(\d+)\/close$/);
  if (closeShift && method === "POST") {
    const shiftId = Number(closeShift[1]);
    const shift = await env.DB.prepare(
      "SELECT * FROM cash_shifts WHERE id=?"
    ).bind(shiftId).first();

    if (!shift) return error("not_found", "شیفت پیدا نشد.", 404);
    if (shift.status !== "open") {
      return error("shift_closed", "این شیفت قبلاً بسته شده است.", 409);
    }
    if (Number(shift.user_id) !== Number(user.id)) {
      return error("forbidden", "هر کاربر فقط می‌تواند شیفت خودش را ببندد.", 403);
    }

    const openOrders = await env.DB.prepare(
      "SELECT COUNT(*) AS count FROM orders WHERE opened_by=? AND status='open'"
    ).bind(user.id).first();
    if (Number(openOrders?.count || 0) > 0) {
      return error(
        "open_orders_exist",
        "قبل از بستن شیفت، همه سفارش‌های باز خودت را تسویه، منتقل یا لغو کن.",
        409,
        { open_orders: Number(openOrders.count) }
      );
    }

    const data = await bodyJson(request);
    const countedCash = intAmount(data.counted_cash);
    if (countedCash === null) {
      return error("invalid_counted_cash", "موجودی واقعی صندوق را وارد کنید.");
    }
    const note = String(data.note || "").trim().slice(0, 500);

    const live = await buildShiftSummary(env, shift);
    const expectedCash = Number(live.expected_cash_live || 0);
    const difference = countedCash - expectedCash;

    await env.DB.prepare(
      `UPDATE cash_shifts
       SET status='closed',
           expected_cash=?,
           counted_cash=?,
           cash_difference=?,
           closing_note=?,
           closed_at=CURRENT_TIMESTAMP
       WHERE id=?`
    ).bind(expectedCash, countedCash, difference, note || null, shiftId).run();

    const closed = await env.DB.prepare(
      "SELECT * FROM cash_shifts WHERE id=?"
    ).bind(shiftId).first();

    await audit(env, user.id, "close_shift", "cash_shift", shiftId, {
      expected_cash: expectedCash,
      counted_cash: countedCash,
      cash_difference: difference,
      note,
    });

    return json({ ok: true, shift: await buildShiftSummary(env, closed) });
  }

  if (path === "/api/users" && method === "GET") {
    if (!requireRole(user, ["admin"])) return error("forbidden", "Admin access required.", 403);
    const result = await env.DB.prepare(
      `SELECT u.id, u.username, u.name, u.role, u.active, u.created_at, u.updated_at,
              (SELECT MAX(a.created_at) FROM audit_logs a
               WHERE a.user_id=u.id AND a.action='login') AS last_login,
              (SELECT MAX(a.created_at) FROM audit_logs a
               WHERE a.user_id=u.id) AS last_activity,
              CASE WHEN EXISTS(
                SELECT 1 FROM cash_shifts s
                WHERE s.user_id=u.id AND s.status='open'
              ) THEN 1 ELSE 0 END AS has_open_shift
       FROM users u
       ORDER BY u.active DESC, u.id`
    ).all();
    return json({ ok: true, users: result.results || [] });
  }

  if (path === "/api/users" && method === "POST") {
    if (!requireRole(user, ["admin"])) return error("forbidden", "Admin access required.", 403);
    const data = await bodyJson(request);
    const username = String(data.username || "").trim().toLowerCase();
    const name = String(data.name || username).trim();
    const password = String(data.password || "");
    const role = ["admin","cashier","staff"].includes(data.role) ? data.role : "staff";

    if (!/^[a-z0-9._-]{3,32}$/.test(username)) return error("invalid_username", "Invalid username.");
    if (name.length < 2 || name.length > 80) return error("invalid_name", "Invalid name.");
    if (password.length < 8 || password.length > 128) return error("invalid_password", "Password must be at least 8 characters.");

    const salt = randomHex(16);
    let passwordHash;
    try {
      passwordHash = await hashPassword(password, salt);
    } catch (e) {
      console.error("create_user_password_hash_failed", e);
      return error("password_hash_failed", "خطا در پردازش امن رمز عبور.", 503);
    }
    try {
      const result = await env.DB.prepare(
        "INSERT INTO users (username,name,role,pin_hash,pin_salt) VALUES (?,?,?,?,?)",
      ).bind(username, name, role, passwordHash, salt).run();
      await audit(env, user.id, "create_user", "user", Number(result.meta.last_row_id), { username, name, role });
      return json({ ok: true, id: result.meta.last_row_id, username, name, role }, 201);
    } catch (e) {
      return error("user_exists", "A user with this username already exists.", 409);
    }
  }

  const userMatch = path.match(/^\/api\/users\/(\d+)$/);
  if (userMatch && method === "PATCH") {
    if (!requireRole(user, ["admin"])) return error("forbidden", "فقط مدیر به مدیریت کاربران دسترسی دارد.", 403);

    const targetId = Number(userMatch[1]);
    const data = await bodyJson(request);
    const target = await env.DB.prepare("SELECT * FROM users WHERE id=?").bind(targetId).first();
    if (!target) return error("not_found", "کاربر پیدا نشد.", 404);

    const username = data.username === undefined
      ? target.username
      : String(data.username).trim().toLowerCase();
    const name = data.name === undefined ? target.name : String(data.name).trim();
    const role = data.role === undefined ? target.role : String(data.role);
    const active = data.active === undefined ? Number(target.active) : (data.active ? 1 : 0);

    if (!/^[a-z0-9._-]{3,32}$/.test(username)) {
      return error("invalid_username", "نام کاربری باید ۳ تا ۳۲ کاراکتر انگلیسی، عدد، نقطه، خط تیره یا زیرخط باشد.");
    }
    if (name.length < 2 || name.length > 80) {
      return error("invalid_name", "نام نمایشی معتبر نیست.");
    }
    if (!["admin","cashier","staff"].includes(role)) {
      return error("invalid_role", "نقش کاربر معتبر نیست.");
    }

    if (targetId === Number(user.id) && active !== 1) {
      return error("cannot_disable_self", "نمی‌توانید حساب فعلی خودتان را غیرفعال کنید.", 409);
    }

    if (target.role === "admin" && (role !== "admin" || active !== 1)) {
      const otherAdmins = await env.DB.prepare(
        "SELECT COUNT(*) AS count FROM users WHERE role='admin' AND active=1 AND id<>?"
      ).bind(targetId).first();
      if (Number(otherAdmins?.count || 0) === 0) {
        return error("last_admin", "آخرین مدیر فعال سیستم را نمی‌توان غیرفعال یا تغییر نقش داد.", 409);
      }
    }

    let passwordChanged = false;
    if (data.password !== undefined && String(data.password).length > 0) {
      const password = String(data.password);
      if (password.length < 8 || password.length > 128) {
        return error("invalid_password", "رمز عبور باید حداقل ۸ کاراکتر باشد.");
      }
      const salt = randomHex(16);
      let passwordHash;
      try {
        passwordHash = await hashPassword(password, salt);
      } catch (e) {
        return error("password_hash_failed", "خطا در پردازش امن رمز عبور.", 503);
      }
      await env.DB.prepare(
        `UPDATE users
         SET username=?, name=?, role=?, active=?,
             pin_hash=?, pin_salt=?, updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(username, name, role, active, passwordHash, salt, targetId).run();
      passwordChanged = true;
    } else {
      await env.DB.prepare(
        `UPDATE users
         SET username=?, name=?, role=?, active=?, updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(username, name, role, active, targetId).run();
    }

    if (passwordChanged || active !== 1) {
      await env.DB.prepare("DELETE FROM sessions WHERE user_id=?").bind(targetId).run();
    }

    await audit(env, user.id, "update_user", "user", targetId, {
      username,
      name,
      role,
      active,
      password_changed: passwordChanged,
    });

    return json({ ok: true, id: targetId, username, name, role, active });
  }

  const userActivity = path.match(/^\/api\/users\/(\d+)\/activity$/);
  if (userActivity && method === "GET") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "فقط مدیر به فعالیت کاربران دسترسی دارد.", 403);
    }
    const targetId = Number(userActivity[1]);
    const target = await env.DB.prepare(
      "SELECT id, username, name, role, active FROM users WHERE id=?"
    ).bind(targetId).first();
    if (!target) return error("not_found", "کاربر پیدا نشد.", 404);

    const logs = await env.DB.prepare(
      `SELECT id, action, entity_type, entity_id, details, created_at
       FROM audit_logs
       WHERE user_id=?
       ORDER BY id DESC
       LIMIT 100`
    ).bind(targetId).all();

    return json({ ok: true, user: target, activity: logs.results || [] });
  }

  if (path === "/api/role-permissions" && method === "GET") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "فقط مدیر به تنظیم مجوز نقش‌ها دسترسی دارد.", 403);
    }

    return json({
      ok: true,
      keys: ROLE_PERMISSION_KEYS,
      roles: {
        admin: await rolePermissions(env, "admin"),
        cashier: await rolePermissions(env, "cashier"),
        staff: await rolePermissions(env, "staff"),
      },
      locked_roles: ["admin","staff"],
    });
  }

  const permissionRole = path.match(/^\/api\/role-permissions\/(admin|cashier|staff)$/);
  if (permissionRole && method === "PATCH") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "فقط مدیر به تنظیم مجوز نقش‌ها دسترسی دارد.", 403);
    }

    const roleName = permissionRole[1];
    if (roleName !== "cashier") {
      return error(
        "role_permissions_locked",
        roleName === "staff"
          ? "مجوزهای شاگرد برای حفظ محدودیت فروش شخصی و نسیه قفل هستند."
          : "مجوزهای مدیر به‌صورت کامل و ثابت فعال هستند.",
        409
      );
    }

    const data = await bodyJson(request);
    const permissions = data.permissions && typeof data.permissions === "object"
      ? data.permissions
      : {};

    const statements = [];
    for (const key of ROLE_PERMISSION_KEYS) {
      if (permissions[key] === undefined) continue;
      const allowed = permissions[key] ? 1 : 0;
      statements.push(
        env.DB.prepare(
          `INSERT INTO role_permissions (role, permission, allowed, updated_at)
           VALUES (?,?,?,CURRENT_TIMESTAMP)
           ON CONFLICT(role,permission)
           DO UPDATE SET allowed=excluded.allowed, updated_at=CURRENT_TIMESTAMP`
        ).bind(roleName, key, allowed)
      );
    }
    if (statements.length) await env.DB.batch(statements);

    const resolved = await rolePermissions(env, roleName);
    await audit(env, user.id, "update_role_permissions", "role", null, {
      role: roleName,
      permissions: resolved,
    });

    return json({ ok: true, role: roleName, permissions: resolved });
  }

  if (path === "/api/debug" && method === "GET") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "این بخش فقط برای مدیر قابل دسترسی است.", 403);
    }
    const counts = {};
    const tables = [
      "users",
      "cafe_tables",
      "orders",
      "order_items",
      "payments",
      "customers",
      "customer_ledger",
      "expenses",
      "hookah_catalog",
      "service_catalog",
      "audit_logs",
      "cash_shifts",
      "inventory_items",
      "inventory_movements",
      "catalog_inventory_links"
    ];

    for (const table of tables) {
      try {
        const row = await env.DB.prepare(`SELECT COUNT(*) AS count FROM ${table}`).first();
        counts[table] = Number(row?.count || 0);
      } catch (e) {
        counts[table] = null;
      }
    }

    const settings = await env.DB.prepare(
      "SELECT key, value FROM app_settings WHERE key IN ('timezone','calendar','currency') ORDER BY key"
    ).all();

    let openOrders = 0;
    try {
      const row = await env.DB.prepare(
        "SELECT COUNT(*) AS count FROM orders WHERE status='open'"
      ).first();
      openOrders = Number(row?.count || 0);
    } catch (e) {}

    return json({
      ok: true,
      service: "TraditionalCafe API",
      worker_version: API_VERSION,
      database: "ready",
      timezone: IRAN_TIME_ZONE,
      utc_now: new Date().toISOString(),
      iran_now: iranIsoLike(),
      jalali_now: jalaliNowDisplay(),
      setup_key_configured: Boolean(env.SETUP_KEY),
      authenticated_user: {
        id: user.id,
        username: user.username,
        name: user.name,
        role: user.role,
      },
      counts,
      open_orders: openOrders,
      settings: settings.results || [],
    });
  }

  if (path === "/api/receipt-settings" && method === "GET") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "تنظیمات رسید فقط برای مدیر قابل دسترسی است.", 403);
    }

    const rows = await env.DB.prepare(
      `SELECT key,value
       FROM app_settings
       WHERE key IN ('receipt_business_name','receipt_phone','receipt_address','receipt_footer')`
    ).all();

    const settings = {
      business_name: "کافه سنتی",
      phone: "",
      address: "",
      footer: "از همراهی شما سپاسگزاریم."
    };

    for (const row of rows.results || []) {
      if (row.key === "receipt_business_name") settings.business_name = row.value || settings.business_name;
      if (row.key === "receipt_phone") settings.phone = row.value || "";
      if (row.key === "receipt_address") settings.address = row.value || "";
      if (row.key === "receipt_footer") settings.footer = row.value || settings.footer;
    }

    return json({ ok:true, settings });
  }

  if (path === "/api/receipt-settings" && (method === "PATCH" || method === "PUT")) {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "تنظیمات رسید فقط برای مدیر قابل تغییر است.", 403);
    }

    const data = await bodyJson(request);
    const values = {
      receipt_business_name: String(data.business_name || "").trim().slice(0, 100),
      receipt_phone: String(data.phone || "").trim().slice(0, 50),
      receipt_address: String(data.address || "").trim().slice(0, 240),
      receipt_footer: String(data.footer || "").trim().slice(0, 240)
    };

    if (values.receipt_business_name.length < 2) {
      return error("invalid_business_name", "نام مجموعه برای رسید معتبر نیست.");
    }
    if (!values.receipt_footer) {
      values.receipt_footer = "از همراهی شما سپاسگزاریم.";
    }

    const statements = Object.entries(values).map(([key,value]) =>
      env.DB.prepare(
        `INSERT INTO app_settings(key,value,updated_at)
         VALUES (?,?,CURRENT_TIMESTAMP)
         ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=CURRENT_TIMESTAMP`
      ).bind(key,value)
    );

    await env.DB.batch(statements);
    await audit(env, user.id, "update_receipt_settings", "app_settings", null, {
      business_name: values.receipt_business_name,
      has_phone: Boolean(values.receipt_phone),
      has_address: Boolean(values.receipt_address)
    });

    return json({
      ok:true,
      settings:{
        business_name:values.receipt_business_name,
        phone:values.receipt_phone,
        address:values.receipt_address,
        footer:values.receipt_footer
      }
    });
  }

  if (path === "/api/dashboard" && method === "GET") {
    const iranToday = iranDateKey();

    if (user.role === "staff") {
      const sales = await env.DB.prepare(
        `SELECT COALESCE(SUM(total),0) AS sales, COUNT(*) AS orders
         FROM orders
         WHERE status='settled' AND opened_by=?
         AND date(closed_at,'+3 hours','+30 minutes') = date(?)`
      ).bind(user.id, iranToday).first();

      const hookahs = await env.DB.prepare(
        `SELECT COALESCE(SUM(oi.qty),0) AS count
         FROM order_items oi
         JOIN orders o ON o.id=oi.order_id
         WHERE oi.item_type='hookah' AND o.opened_by=?
         AND date(oi.created_at,'+3 hours','+30 minutes') = date(?)`
      ).bind(user.id, iranToday).first();

      const payMethods = await env.DB.prepare(
        `SELECT p.method, COALESCE(SUM(p.amount),0) AS amount
         FROM payments p
         JOIN orders o ON o.id=p.order_id
         WHERE o.opened_by=?
         AND date(p.created_at,'+3 hours','+30 minutes') = date(?)
         GROUP BY p.method`
      ).bind(user.id, iranToday).all();

      const credit = await env.DB.prepare(
        `SELECT COALESCE(SUM(p.amount),0) AS amount
         FROM payments p
         JOIN orders o ON o.id=p.order_id
         WHERE o.opened_by=? AND p.method='credit'
         AND date(p.created_at,'+3 hours','+30 minutes') = date(?)`
      ).bind(user.id, iranToday).first();

      const openOrders = await env.DB.prepare(
        "SELECT COUNT(*) AS count FROM orders WHERE opened_by=? AND status='open'"
      ).bind(user.id).first();

      return json({
        ok: true,
        scope: "self",
        sales_today: Number(sales?.sales || 0),
        settled_orders_today: Number(sales?.orders || 0),
        hookahs_today: Number(hookahs?.count || 0),
        credit_today: Number(credit?.amount || 0),
        open_orders: Number(openOrders?.count || 0),
        payment_methods: payMethods.results || [],
        timezone: IRAN_TIME_ZONE,
        iran_date: iranToday,
        jalali_now: jalaliNowDisplay(),
      });
    }

    const sales = await env.DB.prepare(
      `SELECT COALESCE(SUM(total),0) AS sales
       FROM orders
       WHERE status='settled'
       AND date(closed_at,'+3 hours','+30 minutes') = date(?)`
    ).bind(iranToday).first();

    const hookahs = await env.DB.prepare(
      `SELECT COALESCE(SUM(oi.qty),0) AS count
       FROM order_items oi JOIN orders o ON o.id=oi.order_id
       WHERE oi.item_type='hookah'
       AND date(oi.created_at,'+3 hours','+30 minutes') = date(?)`
    ).bind(iranToday).first();

    const expenses = await env.DB.prepare(
      `SELECT COALESCE(SUM(amount),0) AS amount
       FROM expenses
       WHERE date(created_at,'+3 hours','+30 minutes') = date(?)`
    ).bind(iranToday).first();

    const gross = await env.DB.prepare(
      `SELECT COALESCE(SUM(oi.qty * (oi.unit_price - oi.unit_cost)),0) AS amount
       FROM order_items oi JOIN orders o ON o.id=oi.order_id
       WHERE o.status='settled'
       AND date(o.closed_at,'+3 hours','+30 minutes') = date(?)`
    ).bind(iranToday).first();

    const debt = await env.DB.prepare(
      "SELECT COALESCE(SUM(amount),0) AS amount FROM customer_ledger"
    ).first();

    const payMethods = await env.DB.prepare(
      `SELECT method, COALESCE(SUM(amount),0) AS amount
       FROM payments
       WHERE date(created_at,'+3 hours','+30 minutes') = date(?)
       GROUP BY method`
    ).bind(iranToday).all();

    const expenseAmount = Number(expenses?.amount || 0);
    return json({
      ok: true,
      scope: "all",
      sales_today: Number(sales?.sales || 0),
      hookahs_today: Number(hookahs?.count || 0),
      expenses_today: expenseAmount,
      gross_profit_today: Number(gross?.amount || 0),
      net_profit_today: Number(gross?.amount || 0) - expenseAmount,
      total_customer_debt: Number(debt?.amount || 0),
      payment_methods: payMethods.results || [],
      timezone: IRAN_TIME_ZONE,
      iran_date: iranToday,
      jalali_now: jalaliNowDisplay(),
    });
  }

  if (path === "/api/my-sales/today" && method === "GET") {
    const iranToday = iranDateKey();
    const result = await env.DB.prepare(
      `SELECT o.id, o.total, o.discount, o.closed_at, t.name AS table_name,
              COALESCE(SUM(CASE WHEN p.method='credit' THEN p.amount ELSE 0 END),0) AS credit_amount
       FROM orders o
       JOIN cafe_tables t ON t.id=o.table_id
       LEFT JOIN payments p ON p.order_id=o.id
       WHERE o.opened_by=? AND o.status='settled'
       AND date(o.closed_at,'+3 hours','+30 minutes') = date(?)
       GROUP BY o.id
       ORDER BY o.closed_at DESC`
    ).bind(user.id, iranToday).all();

    return json({
      ok: true,
      scope: "self",
      iran_date: iranToday,
      sales: result.results || [],
    });
  }

  if (path === "/api/tables" && method === "GET") {
    if (user.role === "staff") {
      const result = await env.DB.prepare(
        `SELECT t.id, t.name, t.sort_order, t.active,
                CASE WHEN o.id IS NULL THEN 0 ELSE 1 END AS busy,
                CASE WHEN o.opened_by=? THEN 1 ELSE 0 END AS mine,
                CASE WHEN o.opened_by=? THEN o.id ELSE NULL END AS order_id,
                CASE WHEN o.opened_by=? THEN o.total ELSE 0 END AS total,
                CASE WHEN o.opened_by=? THEN o.opened_at ELSE NULL END AS opened_at
         FROM cafe_tables t
         LEFT JOIN orders o ON o.table_id=t.id AND o.status='open'
         WHERE t.active=1
         ORDER BY t.sort_order, t.id`
      ).bind(user.id, user.id, user.id, user.id).all();
      return json({ ok: true, scope: "self", tables: result.results || [] });
    }

    const result = await env.DB.prepare(
      `SELECT t.id, t.name, t.sort_order, t.active,
              CASE WHEN o.id IS NULL THEN 0 ELSE 1 END AS busy,
              1 AS mine,
              o.id AS order_id, o.subtotal, o.discount, o.total, o.opened_at
       FROM cafe_tables t
       LEFT JOIN orders o ON o.table_id=t.id AND o.status='open'
       WHERE t.active=1
       ORDER BY t.sort_order, t.id`
    ).all();
    return json({ ok: true, scope: "all", tables: result.results || [] });
  }

  if (path === "/api/tables" && method === "POST") {
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "فقط مدیر یا صندوق‌دار می‌تواند میز جدید تعریف کند.", 403);
    }
    const data = await bodyJson(request);
    const name = String(data.name || "").trim();
    if (!name) return error("invalid_name", "Table name is required.");
    const max = await env.DB.prepare("SELECT COALESCE(MAX(sort_order),0) AS max_order FROM cafe_tables").first();
    try {
      const result = await env.DB.prepare(
        "INSERT INTO cafe_tables (name, sort_order) VALUES (?,?)",
      ).bind(name, Number(max?.max_order || 0) + 1).run();
      await audit(env, user.id, "create_table", "table", Number(result.meta.last_row_id), { name });
      return json({ ok: true, id: result.meta.last_row_id, name }, 201);
    } catch {
      return error("table_exists", "Table name already exists.", 409);
    }
  }

  const openTable = path.match(/^\/api\/tables\/(\d+)\/open$/);
  if (openTable && method === "POST") {
    const tableId = Number(openTable[1]);
    const table = await env.DB.prepare("SELECT id, name, active FROM cafe_tables WHERE id=?").bind(tableId).first();
    if (!table || Number(table.active) !== 1) return error("not_found", "Table not found.", 404);
    const existing = await env.DB.prepare(
      "SELECT id, opened_by FROM orders WHERE table_id=? AND status='open'"
    ).bind(tableId).first();
    if (existing) {
      if (user.role === "staff" && Number(existing.opened_by) !== Number(user.id)) {
        return error("table_busy", "این میز توسط کاربر دیگری در حال استفاده است.", 409);
      }
      return json({ ok: true, order_id: existing.id, table_id: tableId, resumed: true });
    }

    const shift = await getOpenShift(env, user.id);
    if (!shift) {
      return error("shift_required", "برای ثبت فروش ابتدا شیفت خود را باز کنید.", 409);
    }

    const data = await bodyJson(request);
    const result = await env.DB.prepare(
      "INSERT INTO orders (table_id, opened_by, notes, opened_shift_id) VALUES (?,?,?,?)",
    ).bind(
      tableId,
      user.id,
      data.notes ? String(data.notes) : null,
      shift.id
    ).run();
    await audit(env, user.id, "open_order", "order", Number(result.meta.last_row_id), { table_id: tableId });
    return json({ ok: true, order_id: result.meta.last_row_id, table_id: tableId }, 201);
  }

  if (path === "/api/hookahs" && method === "GET") {
    const query = user.role === "staff"
      ? "SELECT id, name, price, active FROM hookah_catalog WHERE active=1 ORDER BY name"
      : "SELECT id, name, price, cost, active FROM hookah_catalog WHERE active=1 ORDER BY name";
    const result = await env.DB.prepare(query).all();
    return json({ ok: true, items: result.results || [] });
  }

  if (path === "/api/hookahs" && method === "POST") {
    if (!requireRole(user, ["admin"])) return error("forbidden", "Admin access required.", 403);
    const data = await bodyJson(request);
    const name = String(data.name || "").trim();
    const price = intAmount(data.price);
    const cost = intAmount(data.cost);
    if (!name || price === null || cost === null) return error("invalid_input", "Name, price and cost are required.");
    try {
      const result = await env.DB.prepare(
        "INSERT INTO hookah_catalog (name, price, cost) VALUES (?,?,?)",
      ).bind(name, price, cost).run();
      await audit(env, user.id, "create_hookah", "hookah", Number(result.meta.last_row_id), { name, price, cost });
      return json({ ok: true, id: result.meta.last_row_id }, 201);
    } catch {
      return error("hookah_exists", "This hookah item already exists.", 409);
    }
  }


  if (path === "/api/inventory" && method === "GET") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت انبار برای این حساب فعال نیست.", 403);
    }

    const includeAll = url.searchParams.get("all") === "1";
    const result = await env.DB.prepare(
      `SELECT id, name, unit, stock_qty, min_stock, purchase_price, active,
              created_at, updated_at,
              CASE
                WHEN stock_qty <= 0 THEN 'out'
                WHEN stock_qty <= min_stock THEN 'low'
                ELSE 'ok'
              END AS stock_status
       FROM inventory_items
       ${includeAll ? "" : "WHERE active=1"}
       ORDER BY
         CASE WHEN stock_qty <= min_stock THEN 0 ELSE 1 END,
         active DESC, name`
    ).all();

    const summary = await env.DB.prepare(
      `SELECT
          COUNT(*) AS total_items,
          COALESCE(SUM(CASE WHEN active=1 THEN 1 ELSE 0 END),0) AS active_items,
          COALESCE(SUM(CASE WHEN active=1 AND stock_qty<=min_stock THEN 1 ELSE 0 END),0) AS low_stock_items,
          COALESCE(SUM(stock_qty * purchase_price),0) AS inventory_value
       FROM inventory_items`
    ).first();

    return json({
      ok: true,
      items: result.results || [],
      summary: {
        total_items: Number(summary?.total_items || 0),
        active_items: Number(summary?.active_items || 0),
        low_stock_items: Number(summary?.low_stock_items || 0),
        inventory_value: Number(summary?.inventory_value || 0),
      },
    });
  }

  if (path === "/api/inventory" && method === "POST") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت انبار برای این حساب فعال نیست.", 403);
    }

    const data = await bodyJson(request);
    const name = String(data.name || "").trim();
    const unit = String(data.unit || "عدد").trim().slice(0, 30);
    const openingStock = intAmount(data.opening_stock || 0);
    const minStock = intAmount(data.min_stock || 0);
    const purchasePrice = intAmount(data.purchase_price || 0);

    if (!name || name.length > 100 || !unit || openingStock === null ||
        minStock === null || purchasePrice === null) {
      return error("invalid_input", "اطلاعات کالای انبار معتبر نیست.");
    }

    try {
      const result = await env.DB.prepare(
        `INSERT INTO inventory_items
         (name,unit,stock_qty,min_stock,purchase_price,active)
         VALUES (?,?,?,?,?,1)`
      ).bind(name, unit, openingStock, minStock, purchasePrice).run();

      const id = Number(result.meta.last_row_id);

      if (openingStock > 0) {
        await env.DB.prepare(
          `INSERT INTO inventory_movements
           (item_id,movement_type,qty_delta,unit_cost,note,created_by)
           VALUES (?,?,?,?,?,?)`
        ).bind(
          id, "opening", openingStock, purchasePrice,
          "موجودی اولیه", user.id
        ).run();
      }

      await audit(env, user.id, "create_inventory_item", "inventory_item", id, {
        name, unit, opening_stock: openingStock,
        min_stock: minStock, purchase_price: purchasePrice
      });

      return json({
        ok: true, id, name, unit,
        stock_qty: openingStock,
        min_stock: minStock,
        purchase_price: purchasePrice,
        active: 1,
      }, 201);
    } catch (e) {
      return error("inventory_exists", "کالایی با این نام قبلاً در انبار ثبت شده است.", 409);
    }
  }

  const inventoryItem = path.match(/^\/api\/inventory\/(\d+)$/);
  if (inventoryItem && method === "PATCH") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت انبار برای این حساب فعال نیست.", 403);
    }

    const id = Number(inventoryItem[1]);
    const current = await env.DB.prepare(
      "SELECT * FROM inventory_items WHERE id=?"
    ).bind(id).first();
    if (!current) return error("not_found", "کالای انبار پیدا نشد.", 404);

    const data = await bodyJson(request);
    const name = data.name === undefined ? current.name : String(data.name).trim();
    const unit = data.unit === undefined ? current.unit : String(data.unit).trim().slice(0, 30);
    const minStock = data.min_stock === undefined
      ? Number(current.min_stock)
      : intAmount(data.min_stock);
    const purchasePrice = data.purchase_price === undefined
      ? Number(current.purchase_price)
      : intAmount(data.purchase_price);
    const active = data.active === undefined ? Number(current.active) : (data.active ? 1 : 0);

    if (!name || !unit || minStock === null || purchasePrice === null) {
      return error("invalid_input", "اطلاعات کالای انبار معتبر نیست.");
    }

    try {
      await env.DB.prepare(
        `UPDATE inventory_items
         SET name=?, unit=?, min_stock=?, purchase_price=?, active=?,
             updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(name, unit, minStock, purchasePrice, active, id).run();

      if (purchasePrice !== Number(current.purchase_price || 0)) {
        await refreshRecipesUsingInventory(env, id);
      }

      await audit(env, user.id, "update_inventory_item", "inventory_item", id, {
        name, unit, min_stock: minStock, purchase_price: purchasePrice, active
      });

      return json({
        ok: true, id, name, unit,
        stock_qty: Number(current.stock_qty),
        min_stock: minStock,
        purchase_price: purchasePrice,
        active,
      });
    } catch (e) {
      return error("inventory_exists", "کالایی با این نام قبلاً در انبار ثبت شده است.", 409);
    }
  }

  const inventoryMovements = path.match(/^\/api\/inventory\/(\d+)\/movements$/);
  if (inventoryMovements && method === "GET") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت انبار برای این حساب فعال نیست.", 403);
    }

    const itemId = Number(inventoryMovements[1]);
    const item = await env.DB.prepare(
      "SELECT * FROM inventory_items WHERE id=?"
    ).bind(itemId).first();
    if (!item) return error("not_found", "کالای انبار پیدا نشد.", 404);

    const movements = await env.DB.prepare(
      `SELECT m.*, u.name AS created_by_name
       FROM inventory_movements m
       JOIN users u ON u.id=m.created_by
       WHERE m.item_id=?
       ORDER BY m.id DESC
       LIMIT 200`
    ).bind(itemId).all();

    return json({ ok: true, item, movements: movements.results || [] });
  }

  if (inventoryMovements && method === "POST") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت انبار برای این حساب فعال نیست.", 403);
    }

    const itemId = Number(inventoryMovements[1]);
    const item = await env.DB.prepare(
      "SELECT * FROM inventory_items WHERE id=?"
    ).bind(itemId).first();
    if (!item) return error("not_found", "کالای انبار پیدا نشد.", 404);

    const data = await bodyJson(request);
    const movementType = String(data.type || "");
    const qty = intAmount(data.qty);
    const unitCost = data.unit_cost === undefined
      ? Number(item.purchase_price || 0)
      : intAmount(data.unit_cost);
    const note = String(data.note || "").trim().slice(0, 300);

    const incoming = ["purchase","adjustment_in"].includes(movementType);
    const outgoing = ["adjustment_out","waste"].includes(movementType);
    if ((!incoming && !outgoing) || !qty || qty <= 0 || unitCost === null) {
      return error("invalid_movement", "نوع و مقدار گردش انبار معتبر نیست.");
    }

    const delta = incoming ? qty : -qty;
    if (Number(item.stock_qty || 0) + delta < 0) {
      return error("insufficient_stock", "موجودی برای این خروج کافی نیست.", 409, {
        available: Number(item.stock_qty || 0),
        requested: qty,
      });
    }

    await env.DB.batch([
      env.DB.prepare(
        `UPDATE inventory_items
         SET stock_qty=stock_qty+?,
             purchase_price=CASE WHEN ?='purchase' THEN ? ELSE purchase_price END,
             updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(delta, movementType, unitCost, itemId),
      env.DB.prepare(
        `INSERT INTO inventory_movements
         (item_id,movement_type,qty_delta,unit_cost,note,created_by)
         VALUES (?,?,?,?,?,?)`
      ).bind(itemId, movementType, delta, unitCost, note || null, user.id),
    ]);

    if (movementType === "purchase") {
      await refreshRecipesUsingInventory(env, itemId);
    }

    await audit(env, user.id, "inventory_movement", "inventory_item", itemId, {
      type: movementType, qty, delta, unit_cost: unitCost, note
    });

    const updated = await env.DB.prepare(
      "SELECT id,name,unit,stock_qty,min_stock,purchase_price,active FROM inventory_items WHERE id=?"
    ).bind(itemId).first();

    return json({ ok: true, item: updated });
  }

  const recipeMatch = path.match(/^\/api\/recipes\/(hookah|service)\/(\d+)$/);
  if (recipeMatch && method === "GET") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت فرمول مصرف برای این حساب فعال نیست.", 403);
    }

    const catalogType = recipeMatch[1];
    const catalogId = Number(recipeMatch[2]);
    const recipe = await buildRecipe(env, catalogType, catalogId);
    if (!recipe) return error("not_found", "آیتم منو پیدا نشد.", 404);

    return json({ ok: true, recipe });
  }

  if (recipeMatch && method === "PATCH") {
    if (!(await hasPermission(env, user, "manage_inventory"))) {
      return error("forbidden", "مجوز مدیریت فرمول مصرف برای این حساب فعال نیست.", 403);
    }

    const catalogType = recipeMatch[1];
    const catalogId = Number(recipeMatch[2]);
    const tableName = catalogType === "hookah" ? "hookah_catalog" : "service_catalog";
    const catalog = await env.DB.prepare(
      `SELECT id,name FROM ${tableName} WHERE id=?`
    ).bind(catalogId).first();
    if (!catalog) return error("not_found", "آیتم منو پیدا نشد.", 404);

    const data = await bodyJson(request);
    const rawIngredients = Array.isArray(data.ingredients) ? data.ingredients : [];
    if (rawIngredients.length > 30) {
      return error("too_many_ingredients", "تعداد مواد اولیه فرمول بیش از حد مجاز است.");
    }

    const seen = new Set();
    const ingredients = [];
    for (const raw of rawIngredients) {
      const inventoryItemId = Number(raw.inventory_item_id || 0);
      const qtyPerUnit = intAmount(raw.qty_per_unit);
      if (!inventoryItemId || !qtyPerUnit || qtyPerUnit <= 0 || seen.has(inventoryItemId)) {
        return error("invalid_recipe", "مواد اولیه یا مقدار مصرف فرمول معتبر نیست.");
      }

      const inventory = await env.DB.prepare(
        "SELECT id,name,active FROM inventory_items WHERE id=?"
      ).bind(inventoryItemId).first();
      if (!inventory || Number(inventory.active) !== 1) {
        return error("inventory_item_not_found", "یکی از مواد اولیه فعال نیست یا پیدا نشد.", 404);
      }

      seen.add(inventoryItemId);
      ingredients.push({ inventory_item_id: inventoryItemId, qty_per_unit: qtyPerUnit });
    }

    const statements = [
      env.DB.prepare(
        "DELETE FROM catalog_inventory_links WHERE catalog_type=? AND catalog_id=?"
      ).bind(catalogType, catalogId),
    ];

    for (const ingredient of ingredients) {
      statements.push(
        env.DB.prepare(
          `INSERT INTO catalog_inventory_links
           (catalog_type,catalog_id,inventory_item_id,qty_per_unit)
           VALUES (?,?,?,?)`
        ).bind(
          catalogType,
          catalogId,
          ingredient.inventory_item_id,
          ingredient.qty_per_unit
        )
      );
    }

    await env.DB.batch(statements);

    let recipeCost = 0;
    if (ingredients.length > 0) {
      recipeCost = await refreshCatalogRecipeCost(env, catalogType, catalogId);
    } else {
      await env.DB.prepare(
        `UPDATE ${tableName} SET cost=0, updated_at=CURRENT_TIMESTAMP WHERE id=?`
      ).bind(catalogId).run();
    }

    await audit(env, user.id, "update_recipe", catalogType, catalogId, {
      ingredient_count: ingredients.length,
      recipe_cost: recipeCost,
      ingredients,
    });

    return json({
      ok: true,
      recipe: await buildRecipe(env, catalogType, catalogId),
    });
  }

  if (path === "/api/inventory-links" && method === "GET") {
    if (!(await hasPermission(env,user,"manage_inventory"))) {
      return error("forbidden","مجوز مدیریت انبار برای این حساب فعال نیست.",403);
    }

    const rows = await env.DB.prepare(
      `SELECT l.id,
              l.catalog_type AS storage_type,
              CASE
                WHEN l.catalog_type='hookah' THEN 'hookah'
                ELSE COALESCE(s.item_kind,'service')
              END AS catalog_type,
              l.catalog_id,l.inventory_item_id,l.qty_per_unit,
              ii.name AS inventory_name,ii.unit,ii.stock_qty,ii.min_stock,
              CASE
                WHEN l.catalog_type='hookah' THEN h.name
                ELSE s.name
              END AS catalog_name
       FROM catalog_inventory_links l
       JOIN inventory_items ii ON ii.id=l.inventory_item_id
       LEFT JOIN hookah_catalog h
         ON l.catalog_type='hookah' AND h.id=l.catalog_id
       LEFT JOIN service_catalog s
         ON l.catalog_type='service' AND s.id=l.catalog_id
       ORDER BY catalog_type,catalog_name,ii.name`
    ).all();

    return json({ok:true,links:rows.results || []});
  }

  if (path === "/api/inventory-links" && method === "POST") {
    if (!(await hasPermission(env,user,"manage_inventory"))) {
      return error("forbidden","مجوز مدیریت انبار برای این حساب فعال نیست.",403);
    }

    const data = await bodyJson(request);
    const requestedType = String(data.catalog_type || "").toLowerCase();
    const catalogId = Number(data.catalog_id || 0);
    const inventoryItemId = Number(data.inventory_item_id || 0);
    const qtyPerUnit = intAmount(data.qty_per_unit);

    if (!["hookah","drink","food","service"].includes(requestedType) ||
        !catalogId || !inventoryItemId || !qtyPerUnit || qtyPerUnit <= 0) {
      return error("invalid_link","اتصال منو به انبار معتبر نیست.");
    }

    const storageType = requestedType === "hookah" ? "hookah" : "service";
    const catalog = requestedType === "hookah"
      ? await env.DB.prepare(
          "SELECT id,name FROM hookah_catalog WHERE id=?"
        ).bind(catalogId).first()
      : await env.DB.prepare(
          "SELECT id,name,item_kind FROM service_catalog WHERE id=? AND item_kind=?"
        ).bind(catalogId,requestedType).first();

    if (!catalog) return error("catalog_item_not_found","آیتم منو پیدا نشد.",404);

    const inventoryItem = await env.DB.prepare(
      "SELECT id,name FROM inventory_items WHERE id=? AND active=1"
    ).bind(inventoryItemId).first();
    if (!inventoryItem) {
      return error("inventory_item_not_found","کالای فعال انبار پیدا نشد.",404);
    }

    try {
      const result = await env.DB.prepare(
        `INSERT INTO catalog_inventory_links
         (catalog_type,catalog_id,inventory_item_id,qty_per_unit)
         VALUES (?,?,?,?)`
      ).bind(storageType,catalogId,inventoryItemId,qtyPerUnit).run();

      const id = Number(result.meta.last_row_id);
      const recipeCost = await refreshCatalogRecipeCost(env,storageType,catalogId);

      await audit(env,user.id,"create_inventory_link","inventory_link",id,{
        catalog_type:requestedType,
        storage_type:storageType,
        catalog_id:catalogId,
        inventory_item_id:inventoryItemId,
        qty_per_unit:qtyPerUnit,
        recipe_cost:recipeCost
      });

      return json({
        ok:true,id,catalog_type:requestedType,storage_type:storageType,
        recipe_cost:recipeCost
      },201);
    } catch (e) {
      return error("link_exists","این اتصال قبلاً تعریف شده است.",409);
    }
  }

  const inventoryLink = path.match(/^\/api\/inventory-links\/(\d+)$/);
  if (inventoryLink && method === "DELETE") {
    if (!(await hasPermission(env,user,"manage_inventory"))) {
      return error("forbidden","مجوز مدیریت انبار برای این حساب فعال نیست.",403);
    }

    const id = Number(inventoryLink[1]);
    const link = await env.DB.prepare(
      "SELECT * FROM catalog_inventory_links WHERE id=?"
    ).bind(id).first();
    if (!link) return error("not_found","اتصال انبار پیدا نشد.",404);

    await env.DB.prepare(
      "DELETE FROM catalog_inventory_links WHERE id=?"
    ).bind(id).run();

    const recipeCost = await refreshCatalogRecipeCost(
      env,link.catalog_type,Number(link.catalog_id)
    );

    await audit(env,user.id,"delete_inventory_link","inventory_link",id,{
      catalog_type:link.catalog_type,
      catalog_id:Number(link.catalog_id),
      inventory_item_id:Number(link.inventory_item_id),
      recipe_cost:recipeCost
    });

    return json({ok:true,recipe_cost:recipeCost});
  }

  if (path === "/api/catalog-categories" && method === "GET") {
    const section = String(url.searchParams.get("section") || "").toLowerCase();
    if (section && !["hookah","drink","food","service"].includes(section)) {
      return error("invalid_section", "بخش منو معتبر نیست.");
    }

    const canManageCatalog = await hasPermission(env, user, "manage_catalog");
    const includeAll = canManageCatalog && url.searchParams.get("all") === "1";
    const where = [];
    const binds = [];

    if (section) {
      where.push("section=?");
      binds.push(section);
    }
    if (!includeAll) where.push("active=1");

    const rows = await env.DB.prepare(
      `SELECT id,section,name,sort_order,active,created_at,updated_at
       FROM catalog_categories
       ${where.length ? "WHERE " + where.join(" AND ") : ""}
       ORDER BY
         CASE section
           WHEN 'hookah' THEN 1
           WHEN 'drink' THEN 2
           WHEN 'food' THEN 3
           ELSE 4
         END,
         sort_order,name`
    ).bind(...binds).all();

    return json({ ok: true, section: section || null, categories: rows.results || [] });
  }

  if (path === "/api/catalog-categories" && method === "POST") {
    if (!(await hasPermission(env, user, "manage_catalog"))) {
      return error("forbidden", "مجوز مدیریت منو فعال نیست.", 403);
    }

    const data = await bodyJson(request);
    const section = String(data.section || "").toLowerCase();
    const name = String(data.name || "").trim().slice(0, 80);
    const sortOrderRaw = Number(data.sort_order || 0);
    const sortOrder = Number.isFinite(sortOrderRaw) ? Math.round(sortOrderRaw) : 0;
    const active = data.active === undefined ? 1 : (data.active ? 1 : 0);

    if (!["hookah","drink","food","service"].includes(section) || !name) {
      return error("invalid_category", "نام و بخش دسته‌بندی معتبر نیست.");
    }

    try {
      const result = await env.DB.prepare(
        `INSERT INTO catalog_categories
         (section,name,sort_order,active)
         VALUES (?,?,?,?)`
      ).bind(section,name,sortOrder,active).run();

      const id = Number(result.meta.last_row_id);
      await audit(env,user.id,"create_catalog_category","catalog_category",id,{
        section,name,sort_order:sortOrder,active
      });

      return json({
        ok:true,id,section,name,sort_order:sortOrder,active
      },201);
    } catch (e) {
      return error("category_exists","این دسته‌بندی قبلاً در این بخش ثبت شده است.",409);
    }
  }

  const catalogCategory = path.match(/^\/api\/catalog-categories\/(\d+)$/);
  if (catalogCategory && method === "PATCH") {
    if (!(await hasPermission(env, user, "manage_catalog"))) {
      return error("forbidden", "مجوز مدیریت منو فعال نیست.", 403);
    }

    const id = Number(catalogCategory[1]);
    const current = await env.DB.prepare(
      "SELECT * FROM catalog_categories WHERE id=?"
    ).bind(id).first();
    if (!current) return error("not_found","دسته‌بندی پیدا نشد.",404);

    const data = await bodyJson(request);
    const name = data.name === undefined
      ? current.name
      : String(data.name || "").trim().slice(0,80);
    const sortRaw = data.sort_order === undefined
      ? Number(current.sort_order || 0)
      : Number(data.sort_order);
    const sortOrder = Number.isFinite(sortRaw) ? Math.round(sortRaw) : Number(current.sort_order || 0);
    const active = data.active === undefined ? Number(current.active) : (data.active ? 1 : 0);

    if (!name) return error("invalid_category","نام دسته‌بندی معتبر نیست.");

    try {
      await env.DB.prepare(
        `UPDATE catalog_categories
         SET name=?,sort_order=?,active=?,updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(name,sortOrder,active,id).run();

      await audit(env,user.id,"update_catalog_category","catalog_category",id,{
        section:current.section,name,sort_order:sortOrder,active
      });

      return json({
        ok:true,id,section:current.section,name,sort_order:sortOrder,active
      });
    } catch (e) {
      return error("category_exists","این نام دسته‌بندی قبلاً استفاده شده است.",409);
    }
  }

  if (path === "/api/catalog" && method === "GET") {
    const type = String(url.searchParams.get("type") || "").toLowerCase();
    const validTypes = ["hookah","drink","food","service"];
    if (type && !validTypes.includes(type)) {
      return error("invalid_type","بخش منو معتبر نیست.");
    }

    const canManageCatalog = await hasPermission(env, user, "manage_catalog");
    const includeAll = canManageCatalog && url.searchParams.get("all") === "1";
    const includeCost = canManageCatalog;

    const fetchSection = async (section) => {
      if (section === "hookah") {
        const columns = includeCost
          ? "h.id,h.name,h.price,h.cost,h.active,h.description,h.category_id,h.sort_order,h.created_at,h.updated_at"
          : "h.id,h.name,h.price,h.active,h.description,h.category_id,h.sort_order,h.created_at,h.updated_at";

        const rows = await env.DB.prepare(
          `SELECT ${columns},
                  c.name AS category_name,
                  c.sort_order AS category_sort,
                  c.active AS category_active
           FROM hookah_catalog h
           LEFT JOIN catalog_categories c ON c.id=h.category_id
           ${includeAll
             ? ""
             : "WHERE h.active=1 AND (h.category_id IS NULL OR c.active=1)"}
           ORDER BY COALESCE(c.sort_order,999999),c.name,h.sort_order,h.name`
        ).all();
        return rows.results || [];
      }

      const columns = includeCost
        ? "s.id,s.name,s.price,s.cost,s.active,s.description,s.category_id,s.sort_order,s.item_kind,s.created_at,s.updated_at"
        : "s.id,s.name,s.price,s.active,s.description,s.category_id,s.sort_order,s.item_kind,s.created_at,s.updated_at";

      const rows = await env.DB.prepare(
        `SELECT ${columns},
                c.name AS category_name,
                c.sort_order AS category_sort,
                c.active AS category_active
         FROM service_catalog s
         LEFT JOIN catalog_categories c ON c.id=s.category_id
         WHERE s.item_kind=?
           ${includeAll ? "" : "AND s.active=1 AND (s.category_id IS NULL OR c.active=1)"}
         ORDER BY COALESCE(c.sort_order,999999),c.name,s.sort_order,s.name`
      ).bind(section).all();
      return rows.results || [];
    };

    if (type) {
      return json({
        ok:true,
        type,
        items:await fetchSection(type)
      });
    }

    return json({
      ok:true,
      hookahs:await fetchSection("hookah"),
      drinks:await fetchSection("drink"),
      foods:await fetchSection("food"),
      services:await fetchSection("service")
    });
  }

  if (path === "/api/catalog" && method === "POST") {
    if (!(await hasPermission(env, user, "manage_catalog"))) {
      return error("forbidden", "مجوز تعریف و ویرایش منو فعال نیست.", 403);
    }

    const data = await bodyJson(request);
    const type = String(data.type || "").toLowerCase();
    const name = String(data.name || "").trim().slice(0,100);
    const description = String(data.description || "").trim().slice(0,500);
    const price = intAmount(data.price);
    const cost = intAmount(data.cost || 0);
    const categoryId = Number(data.category_id || 0);
    const sortRaw = Number(data.sort_order || 0);
    const sortOrder = Number.isFinite(sortRaw) ? Math.round(sortRaw) : 0;
    const active = data.active === undefined ? 1 : (data.active ? 1 : 0);

    if (!["hookah","drink","food","service"].includes(type)) {
      return error("invalid_type", "نوع منو باید قلیان، نوشیدنی، خوراکی یا خدمت باشد.");
    }
    if (!name || price === null || cost === null) {
      return error("invalid_input", "نام، قیمت فروش و هزینه تمام‌شده معتبر وارد کنید.");
    }

    let categoryName = null;
    if (categoryId > 0) {
      const category = await env.DB.prepare(
        "SELECT id,name FROM catalog_categories WHERE id=? AND section=?"
      ).bind(categoryId,type).first();
      if (!category) {
        return error("category_mismatch","دسته‌بندی انتخاب‌شده متعلق به این بخش منو نیست.",409);
      }
      categoryName = category.name;
    }

    try {
      let result;
      if (type === "hookah") {
        result = await env.DB.prepare(
          `INSERT INTO hookah_catalog
           (name,price,cost,active,description,category_id,sort_order)
           VALUES (?,?,?,?,?,?,?)`
        ).bind(
          name,price,cost,active,description || null,
          categoryId > 0 ? categoryId : null,sortOrder
        ).run();
      } else {
        result = await env.DB.prepare(
          `INSERT INTO service_catalog
           (name,price,cost,active,item_kind,description,category_id,sort_order)
           VALUES (?,?,?,?,?,?,?,?)`
        ).bind(
          name,price,cost,active,type,description || null,
          categoryId > 0 ? categoryId : null,sortOrder
        ).run();
      }

      const id = Number(result.meta.last_row_id);
      await audit(env,user.id,"create_catalog_item",type,id,{
        name,price,cost,active,description,
        category_id:categoryId || null,sort_order:sortOrder
      });

      return json({
        ok:true,id,type,name,price,cost,active,description,
        category_id:categoryId || null,category_name:categoryName,
        sort_order:sortOrder
      },201);
    } catch (e) {
      return error("catalog_exists","موردی با این نام قبلاً در منو تعریف شده است.",409);
    }
  }

  const catalogItem = path.match(/^\/api\/catalog\/(hookah|drink|food|service)\/(\d+)$/);
  if (catalogItem && method === "PATCH") {
    if (!(await hasPermission(env, user, "manage_catalog"))) {
      return error("forbidden", "مجوز تعریف و ویرایش منو فعال نیست.", 403);
    }

    const type = catalogItem[1];
    const id = Number(catalogItem[2]);
    const tableName = type === "hookah" ? "hookah_catalog" : "service_catalog";

    const current = type === "hookah"
      ? await env.DB.prepare(
          "SELECT id,name,price,cost,active,description,category_id,sort_order FROM hookah_catalog WHERE id=?"
        ).bind(id).first()
      : await env.DB.prepare(
          `SELECT id,name,price,cost,active,description,category_id,sort_order,item_kind
           FROM service_catalog WHERE id=? AND item_kind=?`
        ).bind(id,type).first();

    if (!current) return error("not_found","آیتم منو پیدا نشد.",404);

    const data = await bodyJson(request);
    const name = data.name === undefined ? current.name : String(data.name || "").trim().slice(0,100);
    const description = data.description === undefined
      ? (current.description || "")
      : String(data.description || "").trim().slice(0,500);
    const price = data.price === undefined ? Number(current.price) : intAmount(data.price);
    const cost = data.cost === undefined ? Number(current.cost) : intAmount(data.cost);
    const active = data.active === undefined ? Number(current.active) : (data.active ? 1 : 0);
    const categoryId = data.category_id === undefined
      ? Number(current.category_id || 0)
      : Number(data.category_id || 0);
    const sortRaw = data.sort_order === undefined
      ? Number(current.sort_order || 0)
      : Number(data.sort_order);
    const sortOrder = Number.isFinite(sortRaw) ? Math.round(sortRaw) : Number(current.sort_order || 0);

    if (!name || price === null || cost === null) {
      return error("invalid_input","اطلاعات واردشده معتبر نیست.");
    }

    let categoryName = null;
    if (categoryId > 0) {
      const category = await env.DB.prepare(
        "SELECT id,name FROM catalog_categories WHERE id=? AND section=?"
      ).bind(categoryId,type).first();
      if (!category) {
        return error("category_mismatch","دسته‌بندی انتخاب‌شده متعلق به این بخش منو نیست.",409);
      }
      categoryName = category.name;
    }

    try {
      await env.DB.prepare(
        `UPDATE ${tableName}
         SET name=?,price=?,cost=?,active=?,description=?,category_id=?,sort_order=?,
             updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(
        name,price,cost,active,description || null,
        categoryId > 0 ? categoryId : null,sortOrder,id
      ).run();

      await audit(env,user.id,"update_catalog_item",type,id,{
        name,price,cost,active,description,
        category_id:categoryId || null,sort_order:sortOrder
      });

      return json({
        ok:true,id,type,name,price,cost,active,description,
        category_id:categoryId || null,category_name:categoryName,
        sort_order:sortOrder
      });
    } catch (e) {
      return error("catalog_exists","موردی با این نام قبلاً در منو تعریف شده است.",409);
    }
  }

  if (path === "/api/orders" && method === "GET") {
    const requestedStatus = String(url.searchParams.get("status") || "all").toLowerCase();
    const limit = Math.min(200, Math.max(1, Number(url.searchParams.get("limit") || 100)));
    const validStatus = ["open","settled","cancelled"].includes(requestedStatus)
      ? requestedStatus
      : null;

    const where = [];
    const binds = [];

    if (validStatus) {
      where.push("o.status=?");
      binds.push(validStatus);
    }

    const canViewAllOrders = await hasPermission(env, user, "view_all_orders");
    if (!canViewAllOrders) {
      where.push("o.opened_by=?");
      binds.push(user.id);
      where.push("date(o.opened_at,'+3 hours','+30 minutes')=date(?)");
      binds.push(iranDateKey());
    }

    const sql =
      `SELECT o.id, o.table_id, o.opened_by, o.closed_by, o.status,
              o.subtotal, o.discount, o.total, o.notes, o.cancel_reason,
              o.opened_at, o.closed_at, o.updated_at,
              t.name AS table_name,
              u.name AS opened_by_name
       FROM orders o
       JOIN cafe_tables t ON t.id=o.table_id
       JOIN users u ON u.id=o.opened_by
       ${where.length ? "WHERE " + where.join(" AND ") : ""}
       ORDER BY o.id DESC
       LIMIT ${limit}`;

    const result = await env.DB.prepare(sql).bind(...binds).all();
    return json({
      ok: true,
      scope: canViewAllOrders ? "all" : "self_today",
      orders: result.results || [],
    });
  }

  const orderItems = path.match(/^\/api\/orders\/(\d+)\/items$/);
  if (orderItems && method === "POST") {
    const orderId = Number(orderItems[1]);
    const order = await env.DB.prepare(
      "SELECT id,status,opened_by FROM orders WHERE id=?"
    ).bind(orderId).first();

    if (!order || order.status !== "open") {
      return error("order_not_open","سفارش باز پیدا نشد.",404);
    }
    if (user.role === "staff" && Number(order.opened_by) !== Number(user.id)) {
      return error("forbidden","شاگرد فقط می‌تواند سفارش‌های خودش را مدیریت کند.",403);
    }

    const data = await bodyJson(request);
    const qty = Math.max(1,Math.round(Number(data.qty || 1)));

    let type;
    let catalogKind;
    let name;
    let unitPrice;
    let unitCost;

    const catalogType = String(data.catalog_type || "").toLowerCase();
    const catalogId = Number(data.catalog_id || 0);
    const validCatalogTypes = ["hookah","drink","food","service"];

    if (validCatalogTypes.includes(catalogType) && catalogId > 0) {
      const item = catalogType === "hookah"
        ? await env.DB.prepare(
            "SELECT id,name,price,cost,active FROM hookah_catalog WHERE id=?"
          ).bind(catalogId).first()
        : await env.DB.prepare(
            `SELECT id,name,price,cost,active,item_kind
             FROM service_catalog
             WHERE id=? AND item_kind=?`
          ).bind(catalogId,catalogType).first();

      if (!item || Number(item.active) !== 1) {
        return error("catalog_item_not_found","این مورد در منوی فعال پیدا نشد.",404);
      }

      type = catalogType === "hookah" ? "hookah" : "service";
      catalogKind = catalogType;
      name = item.name;
      unitPrice = Number(item.price);
      unitCost = Number(item.cost);
    } else {
      if (user.role === "staff") {
        return error(
          "catalog_required",
          "شاگرد فقط می‌تواند از اقلام تعریف‌شده منو فروش ثبت کند.",
          403
        );
      }

      const requestedKind = String(
        data.catalog_kind || data.item_type || "service"
      ).toLowerCase();

      catalogKind = validCatalogTypes.includes(requestedKind)
        ? requestedKind
        : "service";
      type = catalogKind === "hookah" ? "hookah" : "service";
      name = String(data.name || "").trim();
      unitPrice = intAmount(data.unit_price);
      unitCost = intAmount(data.unit_cost || 0);

      if (!name || unitPrice === null || unitCost === null) {
        return error("invalid_input","اطلاعات آیتم سفارش معتبر نیست.");
      }
    }

    const linkedCatalogId =
      validCatalogTypes.includes(catalogType) && catalogId > 0
        ? catalogId
        : null;

    const result = await env.DB.prepare(
      `INSERT INTO order_items
       (order_id,item_type,catalog_id,catalog_kind,name,qty,unit_price,unit_cost,created_by)
       VALUES (?,?,?,?,?,?,?,?,?)`
    ).bind(
      orderId,type,linkedCatalogId,catalogKind,name,qty,unitPrice,unitCost,user.id
    ).run();

    const totals = await recalcOrder(env,orderId);
    await audit(env,user.id,"add_order_item","order_item",Number(result.meta.last_row_id),{
      order_id:orderId,name,qty,catalog_kind:catalogKind,catalog_id:linkedCatalogId
    });

    return json({
      ok:true,
      id:Number(result.meta.last_row_id),
      catalog_kind:catalogKind,
      totals
    },201);
  }

  const orderItem = path.match(/^\/api\/orders\/(\d+)\/items\/(\d+)$/);
  if (orderItem && (method === "PATCH" || method === "DELETE")) {
    const orderId = Number(orderItem[1]);
    const itemId = Number(orderItem[2]);

    const targetOrder = await env.DB.prepare(
      "SELECT id, status, opened_by FROM orders WHERE id=?"
    ).bind(orderId).first();
    if (!targetOrder || targetOrder.status !== "open") {
      return error("order_not_open", "فقط سفارش باز قابل ویرایش است.", 409);
    }
    if (!canManageOrder(user, targetOrder)) {
      return error("forbidden", "شاگرد فقط می‌تواند سفارش خودش را ویرایش کند.", 403);
    }

    const item = await env.DB.prepare(
      "SELECT id, name, qty FROM order_items WHERE id=? AND order_id=?"
    ).bind(itemId, orderId).first();
    if (!item) return error("not_found", "آیتم سفارش پیدا نشد.", 404);

    if (method === "DELETE") {
      await env.DB.prepare("DELETE FROM order_items WHERE id=? AND order_id=?")
        .bind(itemId, orderId).run();
      const totals = await recalcOrder(env, orderId);
      await audit(env, user.id, "delete_order_item", "order_item", itemId, {
        order_id: orderId,
        name: item.name,
        old_qty: Number(item.qty || 0),
      });
      return json({ ok: true, order_id: orderId, item_id: itemId, totals });
    }

    const data = await bodyJson(request);
    const qty = Math.round(Number(data.qty));
    if (!Number.isFinite(qty) || qty <= 0 || qty > 999) {
      return error("invalid_qty", "تعداد باید بیشتر از صفر باشد.");
    }

    await env.DB.prepare(
      "UPDATE order_items SET qty=? WHERE id=? AND order_id=?"
    ).bind(qty, itemId, orderId).run();
    const totals = await recalcOrder(env, orderId);
    await audit(env, user.id, "update_order_item_qty", "order_item", itemId, {
      order_id: orderId,
      name: item.name,
      old_qty: Number(item.qty || 0),
      new_qty: qty,
    });
    return json({ ok: true, order_id: orderId, item_id: itemId, qty, totals });
  }

  const transferOrder = path.match(/^\/api\/orders\/(\d+)\/transfer$/);
  if (transferOrder && method === "POST") {
    const orderId = Number(transferOrder[1]);
    const current = await env.DB.prepare(
      "SELECT id, table_id, opened_by, status FROM orders WHERE id=?"
    ).bind(orderId).first();
    if (!current || current.status !== "open") {
      return error("order_not_open", "فقط سفارش باز قابل انتقال است.", 409);
    }
    if (!canManageOrder(user, current)) {
      return error("forbidden", "شاگرد فقط می‌تواند سفارش خودش را منتقل کند.", 403);
    }

    const data = await bodyJson(request);
    const tableId = Number(data.table_id || 0);
    if (!tableId || tableId === Number(current.table_id)) {
      return error("invalid_table", "میز مقصد معتبر نیست.");
    }

    const table = await env.DB.prepare(
      "SELECT id, name, active FROM cafe_tables WHERE id=?"
    ).bind(tableId).first();
    if (!table || Number(table.active) !== 1) {
      return error("table_not_found", "میز مقصد پیدا نشد.", 404);
    }

    const occupied = await env.DB.prepare(
      "SELECT id FROM orders WHERE table_id=? AND status='open' LIMIT 1"
    ).bind(tableId).first();
    if (occupied) {
      return error("table_busy", "میز مقصد دارای سفارش باز است.", 409);
    }

    const oldTableId = Number(current.table_id);
    await env.DB.prepare(
      "UPDATE orders SET table_id=?, updated_at=CURRENT_TIMESTAMP WHERE id=?"
    ).bind(tableId, orderId).run();

    await audit(env, user.id, "transfer_order", "order", orderId, {
      from_table_id: oldTableId,
      to_table_id: tableId,
    });

    return json({
      ok: true,
      order_id: orderId,
      from_table_id: oldTableId,
      table_id: tableId,
      table_name: table.name,
    });
  }

  const mergeOrder = path.match(/^\/api\/orders\/(\d+)\/merge$/);
  if (mergeOrder && method === "POST") {
    const targetId = Number(mergeOrder[1]);
    const data = await bodyJson(request);
    const sourceId = Number(data.source_order_id || 0);

    if (!sourceId || sourceId === targetId) {
      return error("invalid_source", "سفارش مبدا برای ادغام معتبر نیست.");
    }

    const target = await env.DB.prepare(
      "SELECT id, table_id, opened_by, status FROM orders WHERE id=?"
    ).bind(targetId).first();
    const source = await env.DB.prepare(
      "SELECT id, table_id, opened_by, status FROM orders WHERE id=?"
    ).bind(sourceId).first();

    if (!target || !source || target.status !== "open" || source.status !== "open") {
      return error("order_not_open", "برای ادغام، هر دو سفارش باید باز باشند.", 409);
    }

    if (user.role === "staff" && (
      Number(target.opened_by) !== Number(user.id) ||
      Number(source.opened_by) !== Number(user.id)
    )) {
      return error("forbidden", "شاگرد فقط می‌تواند سفارش‌های خودش را با هم ادغام کند.", 403);
    }

    await env.DB.batch([
      env.DB.prepare("UPDATE order_items SET order_id=? WHERE order_id=?").bind(targetId, sourceId),
      env.DB.prepare(
        `UPDATE orders
         SET status='cancelled',
             cancel_reason=?,
             closed_by=?,
             closed_at=CURRENT_TIMESTAMP,
             updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind("ادغام با سفارش #" + targetId, user.id, sourceId),
    ]);

    const totals = await recalcOrder(env, targetId);
    await audit(env, user.id, "merge_orders", "order", targetId, {
      source_order_id: sourceId,
      source_table_id: Number(source.table_id),
      target_table_id: Number(target.table_id),
    });

    return json({
      ok: true,
      target_order_id: targetId,
      source_order_id: sourceId,
      totals,
    });
  }

  const cancelOrder = path.match(/^\/api\/orders\/(\d+)\/cancel$/);
  if (cancelOrder && method === "POST") {
    const orderId = Number(cancelOrder[1]);
    const current = await env.DB.prepare(
      "SELECT id, opened_by, status, total FROM orders WHERE id=?"
    ).bind(orderId).first();
    if (!current || current.status !== "open") {
      return error("order_not_open", "فقط سفارش باز قابل لغو است.", 409);
    }
    if (!canManageOrder(user, current)) {
      return error("forbidden", "شاگرد فقط می‌تواند سفارش خودش را لغو کند.", 403);
    }

    const data = await bodyJson(request);
    const reason = String(data.reason || "").trim();
    if (reason.length < 3 || reason.length > 300) {
      return error("reason_required", "دلیل لغو را وارد کنید.");
    }

    await env.DB.prepare(
      `UPDATE orders
       SET status='cancelled',
           cancel_reason=?,
           closed_by=?,
           closed_at=CURRENT_TIMESTAMP,
           updated_at=CURRENT_TIMESTAMP
       WHERE id=?`
    ).bind(reason, user.id, orderId).run();

    await audit(env, user.id, "cancel_order", "order", orderId, {
      reason,
      total: Number(current.total || 0),
    });
    return json({ ok: true, order_id: orderId, status: "cancelled", reason });
  }

  const reverseSettlement = path.match(/^\/api\/orders\/(\d+)\/reverse-settlement$/);
  if (reverseSettlement && method === "POST") {
    if (!(await hasPermission(env, user, "reverse_settlement"))) {
      return error("forbidden", "مجوز برگرداندن تسویه فعال نیست.", 403);
    }

    const orderId = Number(reverseSettlement[1]);
    const current = await env.DB.prepare(
      "SELECT id, status, total, table_id, closed_at, settled_shift_id FROM orders WHERE id=?"
    ).bind(orderId).first();
    if (!current || current.status !== "settled") {
      return error("not_settled", "این سفارش در وضعیت تسویه‌شده نیست.", 409);
    }

    if (current.settled_shift_id) {
      const settledShift = await env.DB.prepare(
        "SELECT status FROM cash_shifts WHERE id=?"
      ).bind(current.settled_shift_id).first();
      if (settledShift && settledShift.status === "closed") {
        return error(
          "closed_shift_locked",
          "تسویه مربوط به یک شیفت بسته است و برای حفظ حساب صندوق قابل بازگشت نیست.",
          409
        );
      }
    }

    const occupied = await env.DB.prepare(
      "SELECT id FROM orders WHERE table_id=? AND status='open' LIMIT 1"
    ).bind(current.table_id).first();
    if (occupied) {
      return error(
        "table_busy",
        "میز این سفارش اکنون سفارش باز دیگری دارد. ابتدا آن سفارش را منتقل یا تسویه کنید.",
        409
      );
    }

    const creditRows = await env.DB.prepare(
      `SELECT customer_id, created_at
       FROM customer_ledger
       WHERE order_id=? AND entry_type='debt'`
    ).bind(orderId).all();

    for (const debt of creditRows.results || []) {
      const laterPayment = await env.DB.prepare(
        `SELECT id FROM customer_ledger
         WHERE customer_id=? AND entry_type='payment' AND created_at>?
         LIMIT 1`
      ).bind(debt.customer_id, debt.created_at).first();
      if (laterPayment) {
        return error(
          "credit_already_changed",
          "بعد از این نسیه، پرداختی در حساب مشتری ثبت شده است؛ ابتدا گردش حساب مشتری را بررسی کنید.",
          409
        );
      }
    }

    const data = await bodyJson(request);
    const reason = String(data.reason || "").trim();
    if (reason.length < 3 || reason.length > 300) {
      return error("reason_required", "دلیل برگرداندن تسویه را وارد کنید.");
    }

    const oldPayments = await env.DB.prepare(
      "SELECT method, amount, customer_id FROM payments WHERE order_id=? ORDER BY id"
    ).bind(orderId).all();

    const inventoryNet = await env.DB.prepare(
      `SELECT item_id, SUM(qty_delta) AS net_delta
       FROM inventory_movements
       WHERE order_id=? AND movement_type IN ('sale','sale_reverse')
       GROUP BY item_id
       HAVING SUM(qty_delta)<0`
    ).bind(orderId).all();

    const reverseStatements = [

      env.DB.prepare("DELETE FROM customer_ledger WHERE order_id=? AND entry_type='debt'").bind(orderId),
      env.DB.prepare("DELETE FROM payments WHERE order_id=?").bind(orderId),
      env.DB.prepare(
        `UPDATE orders
         SET status='open',
             discount=0,
             closed_by=NULL,
             settled_shift_id=NULL,
             closed_at=NULL,
             cancel_reason=NULL,
             updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(orderId),
    ];

    for (const row of inventoryNet.results || []) {
      const restore = Math.abs(Number(row.net_delta || 0));
      if (restore <= 0) continue;
      reverseStatements.push(
        env.DB.prepare(
          "UPDATE inventory_items SET stock_qty=stock_qty+?, updated_at=CURRENT_TIMESTAMP WHERE id=?"
        ).bind(restore, row.item_id)
      );
      reverseStatements.push(
        env.DB.prepare(
          `INSERT INTO inventory_movements
           (item_id,movement_type,qty_delta,unit_cost,order_id,note,created_by)
           VALUES (?,?,?,?,?,?,?)`
        ).bind(
          row.item_id, "sale_reverse", restore, 0, orderId,
          "بازگشت خودکار موجودی پس از اصلاح تسویه", user.id
        )
      );
    }

    await env.DB.batch(reverseStatements);

    const totals = await recalcOrder(env, orderId);
    await audit(env, user.id, "reverse_settlement", "order", orderId, {
      reason,
      previous_total: Number(current.total || 0),
      previous_payments: oldPayments.results || [],
    });

    return json({
      ok: true,
      order_id: orderId,
      status: "open",
      totals,
    });
  }

  const receiptRoute = path.match(/^\/api\/orders\/(\d+)\/receipt$/);
  if (receiptRoute && method === "GET") {
    const orderId = Number(receiptRoute[1]);

    const order = await env.DB.prepare(
      `SELECT o.id,o.table_id,o.opened_by,o.closed_by,o.status,
              o.subtotal,o.discount,o.total,o.notes,o.opened_at,o.closed_at,
              t.name AS table_name,
              opener.name AS opened_by_name,
              closer.name AS closed_by_name
       FROM orders o
       JOIN cafe_tables t ON t.id=o.table_id
       JOIN users opener ON opener.id=o.opened_by
       LEFT JOIN users closer ON closer.id=o.closed_by
       WHERE o.id=?`
    ).bind(orderId).first();

    if (!order) return error("not_found", "سفارش پیدا نشد.", 404);
    if (user.role === "staff" && Number(order.opened_by) !== Number(user.id)) {
      return error("forbidden", "شاگرد فقط می‌تواند رسید فروش خودش را مشاهده کند.", 403);
    }
    if (order.status !== "settled") {
      return error("receipt_not_ready", "رسید نهایی فقط بعد از تسویه سفارش قابل صدور است.", 409);
    }

    const items = await env.DB.prepare(
      `SELECT id,name,qty,unit_price,catalog_kind,item_type,created_at
       FROM order_items
       WHERE order_id=?
       ORDER BY id`
    ).bind(orderId).all();

    const payments = await env.DB.prepare(
      `SELECT p.id,p.method,p.amount,p.customer_id,p.created_at,
              c.name AS customer_name,c.phone AS customer_phone,
              u.name AS received_by_name,
              (
                SELECT l.due_at
                FROM customer_ledger l
                WHERE l.order_id=p.order_id
                  AND l.customer_id=p.customer_id
                  AND l.entry_type='debt'
                ORDER BY l.id DESC
                LIMIT 1
              ) AS due_at
       FROM payments p
       LEFT JOIN customers c ON c.id=p.customer_id
       LEFT JOIN users u ON u.id=p.created_by
       WHERE p.order_id=?
       ORDER BY p.id`
    ).bind(orderId).all();

    const paymentTotals = { cash:0, card:0, transfer:0, credit:0 };
    for (const p of payments.results || []) {
      if (Object.prototype.hasOwnProperty.call(paymentTotals, p.method)) {
        paymentTotals[p.method] += Number(p.amount || 0);
      }
    }

    const settingsRows = await env.DB.prepare(
      `SELECT key,value FROM app_settings
       WHERE key IN ('receipt_business_name','receipt_phone','receipt_address','receipt_footer')`
    ).all();
    const settings = {};
    for (const row of settingsRows.results || []) settings[row.key] = row.value;

    return json({
      ok: true,
      receipt: {
        receipt_number: "TC-" + String(orderId).padStart(6, "0"),
        order_id: orderId,
        business_name: settings.receipt_business_name || "کافه سنتی",
        business_phone: settings.receipt_phone || "",
        business_address: settings.receipt_address || "",
        footer: settings.receipt_footer || "از همراهی شما سپاسگزاریم.",
        table_name: order.table_name,
        opened_by_name: order.opened_by_name,
        cashier_name: order.closed_by_name || "",
        opened_at: order.opened_at,
        issued_at: order.closed_at,
        subtotal: Number(order.subtotal || 0),
        discount: Number(order.discount || 0),
        total: Number(order.total || 0),
        notes: order.notes || "",
        payment_totals: paymentTotals
      },
      items: items.results || [],
      payments: payments.results || [],
      timezone: IRAN_TIME_ZONE
    });
  }

  const orderDetail = path.match(/^\/api\/orders\/(\d+)$/);
  if (orderDetail && method === "GET") {
    const orderId = Number(orderDetail[1]);
    const order = await env.DB.prepare(
      `SELECT o.*, t.name AS table_name, u.name AS opened_by_name
       FROM orders o
       JOIN cafe_tables t ON t.id=o.table_id
       JOIN users u ON u.id=o.opened_by
       WHERE o.id=?`,
    ).bind(orderId).first();
    if (!order) return error("not_found", "Order not found.", 404);
    if (user.role === "staff" && Number(order.opened_by) !== Number(user.id)) {
      return error("forbidden", "شاگرد فقط می‌تواند سفارش‌های خودش را مشاهده کند.", 403);
    }
    const items = user.role === "staff"
      ? await env.DB.prepare(
          "SELECT id, order_id, item_type, catalog_id, catalog_kind, name, qty, unit_price, created_by, created_at FROM order_items WHERE order_id=? ORDER BY id"
        ).bind(orderId).all()
      : await env.DB.prepare(
          "SELECT * FROM order_items WHERE order_id=? ORDER BY id"
        ).bind(orderId).all();
    const payments = await env.DB.prepare("SELECT * FROM payments WHERE order_id=? ORDER BY id").bind(orderId).all();
    return json({ ok: true, order, items: items.results || [], payments: payments.results || [] });
  }

  if (orderDetail && method === "DELETE") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "حذف کامل سفارش فقط برای مدیر مجاز است.", 403);
    }

    const orderId = Number(orderDetail[1]);
    const current = await env.DB.prepare(
      `SELECT o.*, t.name AS table_name, u.username AS opened_by_username
       FROM orders o
       JOIN cafe_tables t ON t.id=o.table_id
       JOIN users u ON u.id=o.opened_by
       WHERE o.id=?`
    ).bind(orderId).first();
    if (!current) return error("not_found", "سفارش پیدا نشد.", 404);

    if (current.settled_shift_id) {
      const settledShift = await env.DB.prepare(
        "SELECT status FROM cash_shifts WHERE id=?"
      ).bind(current.settled_shift_id).first();
      if (settledShift && settledShift.status === "closed") {
        return error(
          "delete_closed_shift_locked",
          "سفارش متعلق به شیفت بسته است و برای حفظ سابقه صندوق قابل حذف کامل نیست.",
          409
        );
      }
    }

    const itemIds = await env.DB.prepare(
      "SELECT id FROM order_items WHERE order_id=?"
    ).bind(orderId).all();

    const inventoryNet = await env.DB.prepare(
      `SELECT item_id, SUM(qty_delta) AS net_delta
       FROM inventory_movements
       WHERE order_id=? AND movement_type IN ('sale','sale_reverse')
       GROUP BY item_id
       HAVING SUM(qty_delta)<0`
    ).bind(orderId).all();

    const snapshot = {
      id: Number(current.id),
      table_id: Number(current.table_id),
      table_name: current.table_name,
      opened_by: current.opened_by_username,
      status: current.status,
      total: Number(current.total || 0),
      opened_at: current.opened_at,
      closed_at: current.closed_at,
    };

    const safetyBackup = await createBackupSnapshot(
      env,
      user.id,
      "pre_delete",
      "before_hard_delete_order:" + orderId,
      { entity_type: "order", entity_id: orderId }
    );

    const statements = [
      env.DB.prepare("DELETE FROM customer_ledger WHERE order_id=?").bind(orderId),
      env.DB.prepare("DELETE FROM payments WHERE order_id=?").bind(orderId),
    ];

    for (const row of inventoryNet.results || []) {
      const restore = Math.abs(Number(row.net_delta || 0));
      if (restore > 0) {
        statements.push(
          env.DB.prepare(
            "UPDATE inventory_items SET stock_qty=stock_qty+?, updated_at=CURRENT_TIMESTAMP WHERE id=?"
          ).bind(restore, row.item_id)
        );
      }
    }

    statements.push(
      env.DB.prepare("DELETE FROM inventory_movements WHERE order_id=?").bind(orderId)
    );

    for (const row of itemIds.results || []) {
      statements.push(
        env.DB.prepare(
          "DELETE FROM audit_logs WHERE entity_type='order_item' AND entity_id=?"
        ).bind(row.id)
      );
    }

    statements.push(
      env.DB.prepare(
        "DELETE FROM audit_logs WHERE entity_type='order' AND entity_id=?"
      ).bind(orderId)
    );
    statements.push(
      env.DB.prepare("DELETE FROM order_items WHERE order_id=?").bind(orderId)
    );
    statements.push(
      env.DB.prepare("DELETE FROM orders WHERE id=?").bind(orderId)
    );

    await env.DB.batch(statements);
    await audit(env, user.id, "hard_delete_order", "deleted_order", orderId, snapshot);

    return json({
      ok: true,
      deleted_order_id: orderId,
      safety_backup_id: safetyBackup.id
    });
  }

  const settle = path.match(/^\/api\/orders\/(\d+)\/settle$/);
  if (settle && method === "POST") {
    const orderId = Number(settle[1]);
    const order = await env.DB.prepare("SELECT * FROM orders WHERE id=?").bind(orderId).first();
    if (!order || order.status !== "open") return error("order_not_open", "Open order not found.", 404);
    if (user.role === "staff" && Number(order.opened_by) !== Number(user.id)) {
      return error("forbidden", "شاگرد فقط می‌تواند فروش خودش را تسویه کند.", 403);
    }

    const shift = await getOpenShift(env, user.id);
    if (!shift) {
      return error("shift_required", "برای تسویه سفارش ابتدا شیفت خود را باز کنید.", 409);
    }

    const data = await bodyJson(request);
    const discount = intAmount(data.discount || 0);
    if (discount === null) return error("invalid_discount", "Invalid discount.");
    if (discount > 0 && !(await hasPermission(env, user, "apply_discount"))) {
      return error("forbidden", "مجوز ثبت تخفیف فعال نیست.", 403);
    }

    await env.DB.prepare("UPDATE orders SET discount=? WHERE id=?").bind(discount, orderId).run();
    const totals = await recalcOrder(env, orderId);
    const payments = Array.isArray(data.payments) ? data.payments : [];
    if (totals.total > 0 && payments.length === 0) return error("payments_required", "Payment is required.");

    let paid = 0;
    const normalized = [];
    const creditPending = new Map();
    for (const p of payments) {
      const methodName = String(p.method || "");
      const amount = intAmount(p.amount);
      if (!["cash","card","transfer","credit"].includes(methodName) || !amount || amount <= 0) {
        return error("invalid_payment", "Invalid payment item.");
      }
      const customerId = p.customer_id ? Number(p.customer_id) : null;
      if (methodName === "credit" && !customerId) {
        return error("customer_required", "برای ثبت نسیه باید مشتری انتخاب شود.");
      }

      let dueAt = null;
      if (customerId) {
        const customer = await env.DB.prepare(
          `SELECT c.id, c.name, c.credit_limit, c.due_days,
                  COALESCE(SUM(l.amount),0) AS balance
           FROM customers c
           LEFT JOIN customer_ledger l ON l.customer_id=c.id
           WHERE c.id=? AND c.active=1
           GROUP BY c.id`
        ).bind(customerId).first();

        if (!customer) return error("customer_not_found", "مشتری پیدا نشد.", 404);

        if (methodName === "credit") {
          const previousPending = creditPending.get(customerId) || 0;
          const currentBalance = Number(customer.balance || 0);
          const limit = Number(customer.credit_limit || 0);
          const afterBalance = currentBalance + previousPending + amount;

          if (limit > 0 && afterBalance > limit) {
            return error(
              "credit_limit_exceeded",
              "سقف اعتبار «" + customer.name + "» کافی نیست.",
              409,
              {
                customer_id: customerId,
                customer_name: customer.name,
                current_balance: currentBalance,
                credit_limit: limit,
                requested_credit: amount,
                balance_after: afterBalance,
                remaining_credit: Math.max(0, limit - currentBalance - previousPending),
              }
            );
          }

          creditPending.set(customerId, previousPending + amount);
          const dueDays = Number(customer.due_days || 0);
          dueAt = dueDays > 0
            ? new Date(Date.now() + dueDays * 86400000).toISOString()
            : null;
        }
      }

      paid += amount;
      normalized.push({
        method: methodName,
        amount,
        customer_id: customerId,
        due_at: dueAt,
      });
    }

    if (paid !== totals.total) {
      return error("payment_mismatch", "Payment total must equal order total.", 409, { expected: totals.total, received: paid });
    }

    const stockLinks = await env.DB.prepare(
      `SELECT oi.id AS order_item_id, oi.name AS order_item_name, oi.qty,
              l.inventory_item_id, l.qty_per_unit,
              ii.name AS inventory_name, ii.stock_qty, ii.unit, ii.purchase_price
       FROM order_items oi
       JOIN catalog_inventory_links l
         ON l.catalog_type=oi.item_type AND l.catalog_id=oi.catalog_id
       JOIN inventory_items ii ON ii.id=l.inventory_item_id
       WHERE oi.order_id=? AND ii.active=1`
    ).bind(orderId).all();

    const requiredByInventory = new Map();
    for (const row of stockLinks.results || []) {
      const inventoryId = Number(row.inventory_item_id);
      const needed = Number(row.qty || 0) * Number(row.qty_per_unit || 0);
      if (needed <= 0) continue;
      const current = requiredByInventory.get(inventoryId) || {
        needed: 0,
        stock: Number(row.stock_qty || 0),
        name: row.inventory_name,
        unit: row.unit,
      };
      current.needed += needed;
      requiredByInventory.set(inventoryId, current);
    }

    for (const [, required] of requiredByInventory) {
      if (required.needed > required.stock) {
        return error(
          "insufficient_stock",
          "موجودی «" + required.name + "» برای تسویه این سفارش کافی نیست.",
          409,
          {
            inventory_name: required.name,
            available: required.stock,
            required: required.needed,
            unit: required.unit,
          }
        );
      }
    }

    const statements = normalized.map((p) =>
      env.DB.prepare(
        "INSERT INTO payments (order_id,method,amount,customer_id,created_by,shift_id) VALUES (?,?,?,?,?,?)"
      ).bind(orderId, p.method, p.amount, p.customer_id, user.id, shift.id)
    );

    for (const p of normalized) {
      if (p.method === "credit") {
        statements.push(
          env.DB.prepare(
            `INSERT INTO customer_ledger
             (customer_id,order_id,entry_type,amount,note,created_by,due_at)
             VALUES (?,?,?,?,?,?,?)`
          ).bind(
            p.customer_id,
            orderId,
            "debt",
            p.amount,
            "نسیه سفارش #" + orderId,
            user.id,
            p.due_at
          )
        );
      }
    }

    for (const [inventoryId, required] of requiredByInventory) {
      statements.push(
        env.DB.prepare(
          `UPDATE inventory_items
           SET stock_qty=stock_qty-?, updated_at=CURRENT_TIMESTAMP
           WHERE id=?`
        ).bind(required.needed, inventoryId)
      );
    }

    for (const row of stockLinks.results || []) {
      const consumed = Number(row.qty || 0) * Number(row.qty_per_unit || 0);
      if (consumed <= 0) continue;
      statements.push(
        env.DB.prepare(
          `INSERT INTO inventory_movements
           (item_id,movement_type,qty_delta,unit_cost,order_id,order_item_id,note,created_by)
           VALUES (?,?,?,?,?,?,?,?)`
        ).bind(
          row.inventory_item_id,
          "sale",
          -consumed,
          Number(row.purchase_price || 0),
          orderId,
          row.order_item_id,
          "مصرف خودکار فروش: " + row.order_item_name,
          user.id
        )
      );
    }

    statements.push(
      env.DB.prepare(
        `UPDATE orders
         SET status='settled', closed_by=?, settled_shift_id=?,
             closed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(user.id, shift.id, orderId)
    );

    await env.DB.batch(statements);

    await audit(env, user.id, "settle_order", "order", orderId, {
      total: totals.total,
      payments: normalized,
      inventory_items: requiredByInventory.size,
    });
    return json({ ok: true, order_id: orderId, totals, payments: normalized });
  }

  if (path === "/api/customers" && method === "GET") {
    const canManageLimits = await hasPermission(env, user, "manage_customer_limits");
    const includeInactive = canManageLimits && url.searchParams.get("all") === "1";
    const query = String(url.searchParams.get("q") || "").trim().toLocaleLowerCase();
    const statusFilter = String(url.searchParams.get("status") || "all").toLowerCase();

    const customersResult = await env.DB.prepare(
      `SELECT id,name,phone,notes,active,credit_limit,due_days,created_at,updated_at
       FROM customers
       ${includeInactive ? "" : "WHERE active=1"}
       ORDER BY name`
    ).all();

    const ledgerResult = await env.DB.prepare(
      `SELECT customer_id,amount,entry_type,created_at,due_at
       FROM customer_ledger
       ORDER BY customer_id,id`
    ).all();

    const ledgerByCustomer = new Map();
    for (const entry of ledgerResult.results || []) {
      const id = Number(entry.customer_id);
      if (!ledgerByCustomer.has(id)) ledgerByCustomer.set(id, []);
      ledgerByCustomer.get(id).push(entry);
    }

    let totalDebt = 0;
    let overdueAmount = 0;
    let debtors = 0;
    let overdueCustomers = 0;
    let nearLimitCustomers = 0;

    const customers = [];
    for (const row of customersResult.results || []) {
      const id = Number(row.id);
      const account = buildCustomerAccountSnapshot(
        ledgerByCustomer.get(id) || [],
        Number(row.due_days || 0)
      );

      const limit = Number(row.credit_limit || 0);
      const balance = Number(account.balance || 0);
      const remainingCredit = limit > 0 ? Math.max(0, limit - balance) : null;
      const usedPercent = limit > 0
        ? Math.min(999, Math.round((Math.max(0, balance) * 100) / limit))
        : null;
      const limitReached = limit > 0 && balance >= limit;
      const nearLimit = limit > 0 && balance > 0 && balance >= Math.floor(limit * 0.8);

      if (balance > 0) {
        debtors++;
        totalDebt += balance;
      }
      if (account.overdue_amount > 0) {
        overdueCustomers++;
        overdueAmount += account.overdue_amount;
      }
      if (nearLimit) nearLimitCustomers++;

      const customer = {
        id,
        name: row.name,
        phone: row.phone || "",
        notes: row.notes || "",
        active: Number(row.active || 0),
        credit_limit: limit,
        due_days: Number(row.due_days || 0),
        balance,
        remaining_credit: remainingCredit,
        credit_used_percent: usedPercent,
        limit_reached: limitReached,
        near_limit: nearLimit,
        overdue_amount: Number(account.overdue_amount || 0),
        oldest_unpaid_at: account.oldest_unpaid_at,
        oldest_due_at: account.oldest_due_at,
        days_overdue: Number(account.days_overdue || 0),
        created_at: row.created_at,
        updated_at: row.updated_at,
      };

      if (query) {
        const haystack = (
          String(customer.name || "") + " " +
          String(customer.phone || "") + " " +
          String(customer.notes || "")
        ).toLocaleLowerCase();
        if (!haystack.includes(query)) continue;
      }

      if (statusFilter === "debt" && balance <= 0) continue;
      if (statusFilter === "overdue" && customer.overdue_amount <= 0) continue;
      if (statusFilter === "limit" && !nearLimit) continue;
      if (statusFilter === "clear" && balance !== 0) continue;

      customers.push(customer);
    }

    customers.sort((a,b) => {
      if ((b.overdue_amount > 0) !== (a.overdue_amount > 0)) {
        return b.overdue_amount > 0 ? 1 : -1;
      }
      if (b.balance !== a.balance) return b.balance - a.balance;
      return String(a.name).localeCompare(String(b.name), "fa");
    });

    return json({
      ok: true,
      customers,
      summary: {
        total_customers: customersResult.results?.length || 0,
        debtors,
        overdue_customers: overdueCustomers,
        near_limit_customers: nearLimitCustomers,
        total_debt: totalDebt,
        overdue_amount: overdueAmount,
      },
    });
  }

  if (path === "/api/customers" && method === "POST") {
    const data = await bodyJson(request);
    const name = String(data.name || "").trim();
    const phone = String(data.phone || "").trim().slice(0, 30);
    const notes = String(data.notes || "").trim().slice(0, 500);
    const canManageLimits = await hasPermission(env, user, "manage_customer_limits");

    if (name.length < 2 || name.length > 80) {
      return error("invalid_name", "نام مشتری معتبر نیست.");
    }

    if (!canManageLimits &&
        (data.credit_limit !== undefined || data.due_days !== undefined)) {
      return error("forbidden", "تنظیم سقف اعتبار و موعد فقط برای حساب مجاز است.", 403);
    }

    const creditLimit = canManageLimits ? intAmount(data.credit_limit || 0) : 0;
    const dueDays = canManageLimits
      ? Math.max(0, Math.min(3650, Math.round(Number(data.due_days ?? 30))))
      : 30;

    if (creditLimit === null || !Number.isFinite(dueDays)) {
      return error("invalid_credit_settings", "تنظیمات اعتبار مشتری معتبر نیست.");
    }

    const result = await env.DB.prepare(
      `INSERT INTO customers
       (name,phone,notes,credit_limit,due_days)
       VALUES (?,?,?,?,?)`
    ).bind(
      name,
      phone || null,
      notes || null,
      creditLimit,
      dueDays
    ).run();

    const id = Number(result.meta.last_row_id);
    await audit(env, user.id, "create_customer", "customer", id, {
      name,
      phone,
      credit_limit: creditLimit,
      due_days: dueDays,
    });

    return json({
      ok: true,
      id,
      name,
      phone,
      notes,
      credit_limit: creditLimit,
      due_days: dueDays,
    }, 201);
  }

  const customerItem = path.match(/^\/api\/customers\/(\d+)$/);
  if (customerItem && method === "PATCH") {
    const customerId = Number(customerItem[1]);
    const current = await env.DB.prepare(
      "SELECT * FROM customers WHERE id=?"
    ).bind(customerId).first();
    if (!current) return error("not_found", "مشتری پیدا نشد.", 404);

    const data = await bodyJson(request);
    const canManageLimits = await hasPermission(env, user, "manage_customer_limits");

    const name = data.name === undefined ? current.name : String(data.name).trim();
    const phone = data.phone === undefined
      ? (current.phone || "")
      : String(data.phone || "").trim().slice(0, 30);
    const notes = data.notes === undefined
      ? (current.notes || "")
      : String(data.notes || "").trim().slice(0, 500);

    let creditLimit = Number(current.credit_limit || 0);
    let dueDays = Number(current.due_days || 0);
    let active = Number(current.active || 0);

    if (data.credit_limit !== undefined || data.due_days !== undefined || data.active !== undefined) {
      if (!canManageLimits) {
        return error("forbidden", "مجوز تغییر سقف اعتبار، موعد یا وضعیت مشتری فعال نیست.", 403);
      }
      if (data.credit_limit !== undefined) {
        const parsed = intAmount(data.credit_limit);
        if (parsed === null) return error("invalid_credit_limit", "سقف اعتبار معتبر نیست.");
        creditLimit = parsed;
      }
      if (data.due_days !== undefined) {
        dueDays = Math.max(0, Math.min(3650, Math.round(Number(data.due_days))));
        if (!Number.isFinite(dueDays)) return error("invalid_due_days", "مهلت پرداخت معتبر نیست.");
      }
      if (data.active !== undefined) active = data.active ? 1 : 0;
    }

    if (name.length < 2 || name.length > 80) {
      return error("invalid_name", "نام مشتری معتبر نیست.");
    }

    const balanceRow = await env.DB.prepare(
      "SELECT COALESCE(SUM(amount),0) AS balance FROM customer_ledger WHERE customer_id=?"
    ).bind(customerId).first();
    const currentBalance = Number(balanceRow?.balance || 0);

    if (active === 0 && currentBalance > 0) {
      return error(
        "customer_has_debt",
        "مشتری بدهکار را نمی‌توان غیرفعال کرد.",
        409,
        { balance: currentBalance }
      );
    }

    if (creditLimit > 0 && currentBalance > creditLimit) {
      return error(
        "credit_limit_below_balance",
        "سقف اعتبار جدید نمی‌تواند کمتر از بدهی فعلی مشتری باشد.",
        409,
        { balance: currentBalance, credit_limit: creditLimit }
      );
    }

    await env.DB.prepare(
      `UPDATE customers
       SET name=?,phone=?,notes=?,credit_limit=?,due_days=?,active=?,
           updated_at=CURRENT_TIMESTAMP
       WHERE id=?`
    ).bind(
      name,
      phone || null,
      notes || null,
      creditLimit,
      dueDays,
      active,
      customerId
    ).run();

    await audit(env, user.id, "update_customer", "customer", customerId, {
      name,
      phone,
      credit_limit: creditLimit,
      due_days: dueDays,
      active,
    });

    return json({
      ok: true,
      id: customerId,
      name,
      phone,
      notes,
      credit_limit: creditLimit,
      due_days: dueDays,
      active,
      balance: currentBalance,
    });
  }

  const customerLedger = path.match(/^\/api\/customers\/(\d+)\/ledger$/);
  if (customerLedger && method === "GET") {
    const customerId = Number(customerLedger[1]);
    const full = url.searchParams.get("full") === "1";
    const limit = full ? 5000 : Math.min(
      500,
      Math.max(1, Number(url.searchParams.get("limit") || 200))
    );
    const beforeId = Math.max(0, Number(url.searchParams.get("before_id") || 0));

    const customer = await env.DB.prepare(
      "SELECT * FROM customers WHERE id=?"
    ).bind(customerId).first();
    if (!customer) return error("not_found", "مشتری پیدا نشد.", 404);

    const allEntries = await env.DB.prepare(
      `SELECT id,customer_id,amount,entry_type,created_at,due_at
       FROM customer_ledger
       WHERE customer_id=?
       ORDER BY id`
    ).bind(customerId).all();

    const account = buildCustomerAccountSnapshot(
      allEntries.results || [],
      Number(customer.due_days || 0)
    );

    const entrySql =
      `SELECT l.id,l.customer_id,l.order_id,l.entry_type,l.amount,l.note,
              l.created_by,l.created_at,l.shift_id,l.payment_method,l.due_at,
              u.name AS created_by_name,
              o.total AS order_total,
              t.name AS table_name
       FROM customer_ledger l
       JOIN users u ON u.id=l.created_by
       LEFT JOIN orders o ON o.id=l.order_id
       LEFT JOIN cafe_tables t ON t.id=o.table_id
       WHERE l.customer_id=? ${beforeId > 0 ? "AND l.id<?" : ""}
       ORDER BY l.id DESC
       LIMIT ${limit}`;

    const entries = beforeId > 0
      ? await env.DB.prepare(entrySql).bind(customerId, beforeId).all()
      : await env.DB.prepare(entrySql).bind(customerId).all();

    let runningBalance = 0;
    let debtEntriesTotal = 0;
    let paymentsTotal = 0;
    let positiveAdjustments = 0;
    let negativeAdjustments = 0;
    const runningById = new Map();

    for (const entry of allEntries.results || []) {
      const amount = Number(entry.amount || 0);
      runningBalance += amount;
      runningById.set(Number(entry.id), runningBalance);

      if (entry.entry_type === "debt" && amount > 0) {
        debtEntriesTotal += amount;
      } else if (entry.entry_type === "payment" && amount < 0) {
        paymentsTotal += Math.abs(amount);
      } else if (entry.entry_type === "adjustment") {
        if (amount > 0) positiveAdjustments += amount;
        if (amount < 0) negativeAdjustments += Math.abs(amount);
      }
    }

    const rows = (entries.results || []).map((row) => ({
      ...row,
      running_balance: runningById.get(Number(row.id)) ?? null,
    }));

    const nextBeforeId = rows.length === limit
      ? Number(rows[rows.length - 1].id)
      : null;

    const limitAmount = Number(customer.credit_limit || 0);
    const balance = Number(account.balance || 0);

    return json({
      ok: true,
      customer: {
        id: Number(customer.id),
        name: customer.name,
        phone: customer.phone || "",
        notes: customer.notes || "",
        active: Number(customer.active || 0),
        credit_limit: limitAmount,
        due_days: Number(customer.due_days || 0),
        balance,
        remaining_credit: limitAmount > 0 ? Math.max(0, limitAmount - balance) : null,
        overdue_amount: Number(account.overdue_amount || 0),
        oldest_unpaid_at: account.oldest_unpaid_at,
        oldest_due_at: account.oldest_due_at,
        days_overdue: Number(account.days_overdue || 0),
      },
      entries: rows,
      statement: {
        debt_entries_total: debtEntriesTotal,
        payments_total: paymentsTotal,
        positive_adjustments: positiveAdjustments,
        negative_adjustments: negativeAdjustments,
        transaction_count: (allEntries.results || []).length,
        current_balance: balance,
      },
      next_before_id: nextBeforeId,
    });
  }

  const customerPayment = path.match(/^\/api\/customers\/(\d+)\/payment$/);
  if (customerPayment && method === "POST") {
    const customerId = Number(customerPayment[1]);
    const customer = await env.DB.prepare(
      `SELECT c.id,c.name,c.active,COALESCE(SUM(l.amount),0) AS balance
       FROM customers c
       LEFT JOIN customer_ledger l ON l.customer_id=c.id
       WHERE c.id=?
       GROUP BY c.id`
    ).bind(customerId).first();

    if (!customer || Number(customer.active) !== 1) {
      return error("not_found", "مشتری فعال پیدا نشد.", 404);
    }

    const shift = await getOpenShift(env, user.id);
    if (!shift) {
      return error("shift_required", "برای دریافت بدهی مشتری ابتدا شیفت خود را باز کنید.", 409);
    }

    const data = await bodyJson(request);
    const amount = intAmount(data.amount);
    const paymentMethod = ["cash","card","transfer"].includes(
      String(data.payment_method || "cash")
    ) ? String(data.payment_method || "cash") : "cash";
    const note = String(data.note || "").trim().slice(0, 300);

    if (!amount || amount <= 0) {
      return error("invalid_amount", "مبلغ پرداخت باید بیشتر از صفر باشد.");
    }

    const currentBalance = Number(customer.balance || 0);
    if (currentBalance <= 0) {
      return error("no_debt", "این مشتری بدهی قابل پرداخت ندارد.", 409);
    }
    if (amount > currentBalance) {
      return error(
        "payment_exceeds_balance",
        "مبلغ پرداخت نمی‌تواند بیشتر از مانده بدهی باشد.",
        409,
        { balance: currentBalance, requested: amount }
      );
    }

    await env.DB.prepare(
      `INSERT INTO customer_ledger
       (customer_id,entry_type,amount,note,created_by,shift_id,payment_method)
       VALUES (?,?,?,?,?,?,?)`
    ).bind(
      customerId,
      "payment",
      -amount,
      note || "پرداخت بدهی",
      user.id,
      shift.id,
      paymentMethod
    ).run();

    const balanceAfter = currentBalance - amount;

    await audit(env, user.id, "customer_payment", "customer", customerId, {
      amount,
      payment_method: paymentMethod,
      shift_id: Number(shift.id),
      balance_before: currentBalance,
      balance_after: balanceAfter,
      note,
    });

    return json({
      ok: true,
      shift_id: Number(shift.id),
      payment_method: paymentMethod,
      balance_before: currentBalance,
      balance_after: balanceAfter,
    });
  }

  const customerAdjustment = path.match(/^\/api\/customers\/(\d+)\/adjustment$/);
  if (customerAdjustment && method === "POST") {
    if (!(await hasPermission(env, user, "adjust_customer_ledger"))) {
      return error("forbidden", "مجوز اصلاح دستی حساب مشتری فعال نیست.", 403);
    }

    const customerId = Number(customerAdjustment[1]);
    const customer = await env.DB.prepare(
      `SELECT c.id,c.name,c.active,c.credit_limit,c.due_days,
              COALESCE(SUM(l.amount),0) AS balance
       FROM customers c
       LEFT JOIN customer_ledger l ON l.customer_id=c.id
       WHERE c.id=?
       GROUP BY c.id`
    ).bind(customerId).first();

    if (!customer) return error("not_found", "مشتری پیدا نشد.", 404);

    const data = await bodyJson(request);
    const direction = String(data.direction || "");
    const amount = intAmount(data.amount);
    const note = String(data.note || "").trim().slice(0, 300);

    if (!["debt","credit"].includes(direction) || !amount || amount <= 0) {
      return error("invalid_adjustment", "نوع و مبلغ اصلاح حساب معتبر نیست.");
    }
    if (note.length < 3) {
      return error("adjustment_note_required", "برای اصلاح دستی حساب، توضیح الزامی است.");
    }

    const currentBalance = Number(customer.balance || 0);
    const signedAmount = direction === "debt" ? amount : -amount;
    const balanceAfter = currentBalance + signedAmount;

    if (direction === "credit" && amount > Math.max(0, currentBalance)) {
      return error(
        "adjustment_exceeds_balance",
        "کاهش دستی نمی‌تواند بیشتر از بدهی فعلی باشد.",
        409,
        { balance: currentBalance, requested: amount }
      );
    }

    const creditLimit = Number(customer.credit_limit || 0);
    if (direction === "debt" && creditLimit > 0 && balanceAfter > creditLimit) {
      return error(
        "credit_limit_exceeded",
        "اصلاح افزایشی از سقف اعتبار مشتری عبور می‌کند.",
        409,
        {
          balance: currentBalance,
          credit_limit: creditLimit,
          requested: amount,
          balance_after: balanceAfter,
        }
      );
    }

    const dueDays = Number(customer.due_days || 0);
    const dueAt = direction === "debt" && dueDays > 0
      ? new Date(Date.now() + dueDays * 86400000).toISOString()
      : null;

    const result = await env.DB.prepare(
      `INSERT INTO customer_ledger
       (customer_id,entry_type,amount,note,created_by,due_at)
       VALUES (?,?,?,?,?,?)`
    ).bind(
      customerId,
      "adjustment",
      signedAmount,
      note,
      user.id,
      dueAt
    ).run();

    const id = Number(result.meta.last_row_id);
    await audit(env, user.id, "customer_ledger_adjustment", "customer", customerId, {
      ledger_entry_id: id,
      direction,
      amount,
      signed_amount: signedAmount,
      balance_before: currentBalance,
      balance_after: balanceAfter,
      note,
    });

    return json({
      ok: true,
      id,
      customer_id: customerId,
      direction,
      amount,
      balance_before: currentBalance,
      balance_after: balanceAfter,
      due_at: dueAt,
    }, 201);
  }

  if (path === "/api/expenses" && method === "GET") {
    if (!(await hasPermission(env, user, "manage_expenses"))) {
      return error("forbidden", "مجوز مشاهده هزینه‌ها فعال نیست.", 403);
    }
    const result = await env.DB.prepare(
      `SELECT e.*, u.name AS created_by_name
       FROM expenses e JOIN users u ON u.id=e.created_by
       ORDER BY e.id DESC LIMIT 200`,
    ).all();
    return json({ ok: true, expenses: result.results || [] });
  }

  if (path === "/api/expenses" && method === "POST") {
    if (!(await hasPermission(env, user, "manage_expenses"))) {
      return error("forbidden", "مجوز ثبت هزینه فعال نیست.", 403);
    }
    const shift = await getOpenShift(env, user.id);
    if (!shift) {
      return error("shift_required", "برای ثبت هزینه ابتدا شیفت خود را باز کنید.", 409);
    }

    const data = await bodyJson(request);
    const category = String(data.category || "").trim();
    const amount = intAmount(data.amount);
    const paymentMethod = ["cash","card","transfer"].includes(String(data.payment_method || "cash"))
      ? String(data.payment_method || "cash")
      : "cash";

    if (!category || !amount || amount <= 0) {
      return error("invalid_input", "Category and amount are required.");
    }

    const result = await env.DB.prepare(
      "INSERT INTO expenses (category,amount,description,created_by,shift_id,payment_method) VALUES (?,?,?,?,?,?)",
    ).bind(
      category,
      amount,
      data.description ? String(data.description) : null,
      user.id,
      shift.id,
      paymentMethod
    ).run();

    await audit(env, user.id, "create_expense", "expense", Number(result.meta.last_row_id), {
      category,
      amount,
      payment_method: paymentMethod,
      shift_id: Number(shift.id),
    });
    return json({ ok: true, id: result.meta.last_row_id }, 201);
  }

  if (path === "/api/reports/daily" && method === "GET") {
    if (!(await hasPermission(env, user, "view_reports"))) {
      return error("forbidden", "مجوز مشاهده گزارش‌های مدیریتی فعال نیست.", 403);
    }

    const reportDate = String(url.searchParams.get("date") || iranDateKey()).trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(reportDate)) {
      return error("invalid_date", "تاریخ گزارش معتبر نیست.");
    }

    const validDate = await env.DB.prepare("SELECT date(?) AS value").bind(reportDate).first();
    if (!validDate?.value || validDate.value !== reportDate) {
      return error("invalid_date", "تاریخ گزارش معتبر نیست.");
    }

    const summary = await env.DB.prepare(
      `SELECT COALESCE(SUM(subtotal),0) AS subtotal,
              COALESCE(SUM(discount),0) AS discounts,
              COALESCE(SUM(total),0) AS sales,
              COUNT(*) AS settled_orders
       FROM orders
       WHERE status='settled'
         AND date(closed_at,'+3 hours','+30 minutes')=date(?)`
    ).bind(reportDate).first();

    const costs = await env.DB.prepare(
      `SELECT COALESCE(SUM(oi.qty * oi.unit_cost),0) AS cogs,
              COALESCE(SUM(oi.qty),0) AS sold_units
       FROM order_items oi
       JOIN orders o ON o.id=oi.order_id
       WHERE o.status='settled'
         AND date(o.closed_at,'+3 hours','+30 minutes')=date(?)`
    ).bind(reportDate).first();

    const expenses = await env.DB.prepare(
      `SELECT COALESCE(SUM(amount),0) AS amount, COUNT(*) AS count
       FROM expenses
       WHERE date(created_at,'+3 hours','+30 minutes')=date(?)`
    ).bind(reportDate).first();

    const paymentMethods = await env.DB.prepare(
      `SELECT p.method, COALESCE(SUM(p.amount),0) AS amount, COUNT(*) AS count
       FROM payments p
       JOIN orders o ON o.id=p.order_id
       WHERE o.status='settled'
         AND date(o.closed_at,'+3 hours','+30 minutes')=date(?)
       GROUP BY p.method
       ORDER BY CASE p.method
         WHEN 'cash' THEN 1 WHEN 'card' THEN 2
         WHEN 'transfer' THEN 3 ELSE 4 END`
    ).bind(reportDate).all();

    const categorySales = await env.DB.prepare(
      `SELECT COALESCE(
                oi.catalog_kind,
                CASE WHEN oi.item_type='hookah' THEN 'hookah' ELSE 'service' END
              ) AS catalog_kind,
              COALESCE(SUM(oi.qty),0) AS qty,
              COALESCE(SUM(oi.qty * oi.unit_price),0) AS sales_before_discount,
              COALESCE(SUM(oi.qty * oi.unit_cost),0) AS cost
       FROM order_items oi
       JOIN orders o ON o.id=oi.order_id
       WHERE o.status='settled'
         AND date(o.closed_at,'+3 hours','+30 minutes')=date(?)
       GROUP BY catalog_kind
       ORDER BY sales_before_discount DESC`
    ).bind(reportDate).all();

    const topItems = await env.DB.prepare(
      `SELECT COALESCE(
                oi.catalog_kind,
                CASE WHEN oi.item_type='hookah' THEN 'hookah' ELSE 'service' END
              ) AS catalog_kind,
              oi.name,
              COALESCE(SUM(oi.qty),0) AS qty,
              COALESCE(SUM(oi.qty * oi.unit_price),0) AS sales_before_discount
       FROM order_items oi
       JOIN orders o ON o.id=oi.order_id
       WHERE o.status='settled'
         AND date(o.closed_at,'+3 hours','+30 minutes')=date(?)
       GROUP BY catalog_kind,oi.name
       ORDER BY qty DESC,sales_before_discount DESC,oi.name
       LIMIT 12`
    ).bind(reportDate).all();

    const staffSales = await env.DB.prepare(
      `SELECT u.id AS user_id,u.name AS user_name,u.role,
              COUNT(o.id) AS settled_orders,
              COALESCE(SUM(o.total),0) AS sales,
              COALESCE(SUM(o.discount),0) AS discounts
       FROM orders o
       JOIN users u ON u.id=o.opened_by
       WHERE o.status='settled'
         AND date(o.closed_at,'+3 hours','+30 minutes')=date(?)
       GROUP BY u.id,u.name,u.role
       ORDER BY sales DESC,user_name`
    ).bind(reportDate).all();

    const expenseCategories = await env.DB.prepare(
      `SELECT category,COALESCE(SUM(amount),0) AS amount,COUNT(*) AS count
       FROM expenses
       WHERE date(created_at,'+3 hours','+30 minutes')=date(?)
       GROUP BY category
       ORDER BY amount DESC,category`
    ).bind(reportDate).all();

    const hourlySales = await env.DB.prepare(
      `SELECT strftime('%H',closed_at,'+3 hours','+30 minutes') AS hour,
              COUNT(*) AS orders,
              COALESCE(SUM(total),0) AS sales
       FROM orders
       WHERE status='settled'
         AND date(closed_at,'+3 hours','+30 minutes')=date(?)
       GROUP BY hour
       ORDER BY hour`
    ).bind(reportDate).all();

    const creditFlow = await env.DB.prepare(
      `SELECT
          COALESCE(SUM(CASE WHEN entry_type='debt' AND amount>0 THEN amount ELSE 0 END),0) AS credit_created,
          COALESCE(SUM(CASE WHEN entry_type='payment' AND amount<0 THEN -amount ELSE 0 END),0) AS debt_collections,
          COALESCE(SUM(CASE WHEN entry_type='adjustment' THEN amount ELSE 0 END),0) AS adjustments
       FROM customer_ledger
       WHERE date(created_at,'+3 hours','+30 minutes')=date(?)`
    ).bind(reportDate).first();

    const shifts = await env.DB.prepare(
      `SELECT COUNT(*) AS closed_shifts,
              COALESCE(SUM(cash_difference),0) AS cash_difference
       FROM cash_shifts
       WHERE status='closed'
         AND date(closed_at,'+3 hours','+30 minutes')=date(?)`
    ).bind(reportDate).first();

    const previous = await env.DB.prepare(
      `SELECT COALESCE(SUM(total),0) AS sales,COUNT(*) AS settled_orders
       FROM orders
       WHERE status='settled'
         AND date(closed_at,'+3 hours','+30 minutes')=date(?,'-1 day')`
    ).bind(reportDate).first();

    const previousCosts = await env.DB.prepare(
      `SELECT COALESCE(SUM(oi.qty * oi.unit_cost),0) AS cogs
       FROM order_items oi
       JOIN orders o ON o.id=oi.order_id
       WHERE o.status='settled'
         AND date(o.closed_at,'+3 hours','+30 minutes')=date(?,'-1 day')`
    ).bind(reportDate).first();

    const previousExpenses = await env.DB.prepare(
      `SELECT COALESCE(SUM(amount),0) AS amount
       FROM expenses
       WHERE date(created_at,'+3 hours','+30 minutes')=date(?,'-1 day')`
    ).bind(reportDate).first();

    const previousDate = await env.DB.prepare(
      "SELECT date(?,'-1 day') AS value"
    ).bind(reportDate).first();

    const sales = Number(summary?.sales || 0);
    const cogs = Number(costs?.cogs || 0);
    const expenseAmount = Number(expenses?.amount || 0);
    const grossProfit = sales - cogs;
    const netProfit = grossProfit - expenseAmount;
    const settledOrders = Number(summary?.settled_orders || 0);

    const previousSales = Number(previous?.sales || 0);
    const previousNetProfit =
      previousSales -
      Number(previousCosts?.cogs || 0) -
      Number(previousExpenses?.amount || 0);

    return json({
      ok:true,
      date:reportDate,
      timezone:IRAN_TIME_ZONE,
      generated_at:new Date().toISOString(),
      summary:{
        subtotal:Number(summary?.subtotal || 0),
        discounts:Number(summary?.discounts || 0),
        sales,
        settled_orders:settledOrders,
        average_ticket:settledOrders > 0 ? Math.round(sales / settledOrders) : 0,
        sold_units:Number(costs?.sold_units || 0),
        cogs,
        expenses:expenseAmount,
        expense_count:Number(expenses?.count || 0),
        gross_profit:grossProfit,
        net_profit:netProfit,
        profit_margin_percent:sales > 0
          ? Math.round((netProfit * 10000) / sales) / 100
          : 0
      },
      comparison:{
        previous_date:previousDate?.value || null,
        previous_sales:previousSales,
        previous_orders:Number(previous?.settled_orders || 0),
        previous_net_profit:previousNetProfit,
        sales_change_percent:previousSales > 0
          ? Math.round(((sales - previousSales) * 10000) / previousSales) / 100
          : null,
        net_profit_change_percent:previousNetProfit !== 0
          ? Math.round(((netProfit - previousNetProfit) * 10000) / Math.abs(previousNetProfit)) / 100
          : null
      },
      payment_methods:paymentMethods.results || [],
      category_sales:categorySales.results || [],
      top_items:topItems.results || [],
      staff_sales:staffSales.results || [],
      expense_categories:expenseCategories.results || [],
      hourly_sales:hourlySales.results || [],
      credit_flow:{
        credit_created:Number(creditFlow?.credit_created || 0),
        debt_collections:Number(creditFlow?.debt_collections || 0),
        adjustments:Number(creditFlow?.adjustments || 0)
      },
      shifts:{
        closed_shifts:Number(shifts?.closed_shifts || 0),
        cash_difference:Number(shifts?.cash_difference || 0)
      }
    });
  }

  if (path === "/api/reports/summary" && method === "GET") {
    if (!(await hasPermission(env, user, "view_reports"))) {
      return error("forbidden", "مجوز مشاهده گزارش‌ها فعال نیست.", 403);
    }
    const from = url.searchParams.get("from") || "1970-01-01";
    const to = url.searchParams.get("to") || "2999-12-31";

    const sales = await env.DB.prepare(
      "SELECT COALESCE(SUM(total),0) AS amount, COUNT(*) AS count FROM orders WHERE status='settled' AND date(closed_at,'+3 hours','+30 minutes') BETWEEN date(?) AND date(?)",
    ).bind(from, to).first();
    const expenses = await env.DB.prepare(
      "SELECT COALESCE(SUM(amount),0) AS amount FROM expenses WHERE date(created_at,'+3 hours','+30 minutes') BETWEEN date(?) AND date(?)",
    ).bind(from, to).first();
    const gross = await env.DB.prepare(
      `SELECT COALESCE(SUM(oi.qty * (oi.unit_price - oi.unit_cost)),0) AS amount
       FROM order_items oi JOIN orders o ON o.id=oi.order_id
       WHERE o.status='settled' AND date(o.closed_at,'+3 hours','+30 minutes') BETWEEN date(?) AND date(?)`,
    ).bind(from, to).first();
    const hookahs = await env.DB.prepare(
      `SELECT COALESCE(SUM(oi.qty),0) AS count
       FROM order_items oi JOIN orders o ON o.id=oi.order_id
       WHERE oi.item_type='hookah' AND date(oi.created_at,'+3 hours','+30 minutes') BETWEEN date(?) AND date(?)`,
    ).bind(from, to).first();

    return json({
      ok: true,
      from,
      to,
      sales: Number(sales?.amount || 0),
      settled_orders: Number(sales?.count || 0),
      expenses: Number(expenses?.amount || 0),
      gross_profit: Number(gross?.amount || 0),
      net_profit: Number(gross?.amount || 0) - Number(expenses?.amount || 0),
      hookahs: Number(hookahs?.count || 0),
    });
  }

  if (path === "/api/backups" && method === "GET") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "مدیریت بکاپ فقط برای مدیر مجاز است.", 403);
    }

    const limit = Math.min(100, Math.max(1, Number(url.searchParams.get("limit") || 50)));
    const rows = await env.DB.prepare(
      `SELECT id,kind,reason,status,created_by,created_at,completed_at,
              table_count,row_count,size_bytes,checksum,metadata
       FROM backup_snapshots
       ORDER BY created_at DESC,id DESC
       LIMIT ${limit}`
    ).all();

    const summary = await env.DB.prepare(
      `SELECT
          COUNT(*) AS total,
          COALESCE(SUM(CASE WHEN status='ready' THEN 1 ELSE 0 END),0) AS ready,
          COALESCE(SUM(CASE WHEN kind='daily' AND status='ready' THEN 1 ELSE 0 END),0) AS daily,
          COALESCE(SUM(size_bytes),0) AS total_bytes
       FROM backup_snapshots`
    ).first();

    const last = await env.DB.prepare(
      `SELECT id,kind,reason,status,created_at,completed_at,table_count,row_count,size_bytes,checksum
       FROM backup_snapshots
       WHERE status='ready'
       ORDER BY created_at DESC,id DESC
       LIMIT 1`
    ).first();

    return json({
      ok: true,
      timezone: IRAN_TIME_ZONE,
      last_backup: last || null,
      summary: {
        total: Number(summary?.total || 0),
        ready: Number(summary?.ready || 0),
        daily: Number(summary?.daily || 0),
        total_bytes: Number(summary?.total_bytes || 0)
      },
      backups: rows.results || []
    });
  }

  if (path === "/api/backups" && method === "POST") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "ساخت بکاپ فقط برای مدیر مجاز است.", 403);
    }

    const data = await bodyJson(request);
    const reason = String(data.reason || "manual").trim().slice(0, 240);
    const backup = await createBackupSnapshot(
      env,
      user.id,
      "manual",
      reason || "manual",
      { requested_from: "api" }
    );
    await pruneAutomaticBackups(env);
    await audit(env, user.id, "create_backup", "backup_snapshot", null, {
      backup_id: backup.id,
      kind: backup.kind,
      row_count: Number(backup.row_count || 0),
      size_bytes: Number(backup.size_bytes || 0)
    });

    return json({ ok: true, backup }, 201);
  }

  const backupExport = path.match(/^\/api\/backups\/([^/]+)\/export$/);
  if (backupExport && method === "GET") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "خروجی بکاپ فقط برای مدیر مجاز است.", 403);
    }

    const snapshotId = decodeURIComponent(backupExport[1]);
    let loaded;
    try {
      loaded = await loadBackupSnapshot(env, snapshotId);
    } catch (e) {
      if (e && e.code === "backup_integrity_failed") {
        return error(
          "backup_integrity_failed",
          "صحت نسخه پشتیبان تایید نشد و خروجی متوقف شد.",
          409
        );
      }
      throw e;
    }
    if (!loaded) return error("backup_not_found", "بکاپ آماده پیدا نشد.", 404);

    const format = String(url.searchParams.get("format") || "json").toLowerCase();
    const onlyTable = String(url.searchParams.get("table") || "").trim();

    if (onlyTable && !Object.prototype.hasOwnProperty.call(loaded.tables, onlyTable)) {
      return error("backup_table_not_found", "جدول در این بکاپ وجود ندارد.", 404);
    }

    if (format === "csv") {
      const body = backupCsv(loaded.tables, onlyTable);
      const suffix = onlyTable ? "-" + onlyTable : "";
      return downloadResponse(
        "\uFEFF" + body,
        "text/csv; charset=utf-8",
        "traditionalcafe-" + snapshotId + suffix + ".csv"
      );
    }

    const payload = {
      format: "traditionalcafe-backup-v1",
      snapshot: loaded.snapshot,
      exported_at: new Date().toISOString(),
      tables: onlyTable
        ? { [onlyTable]: loaded.tables[onlyTable] || [] }
        : loaded.tables
    };
    return downloadResponse(
      JSON.stringify(payload, null, 2),
      "application/json; charset=utf-8",
      "traditionalcafe-" + snapshotId + ".json"
    );
  }

  const backupRestore = path.match(/^\/api\/backups\/([^/]+)\/restore$/);
  if (backupRestore && method === "POST") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "بازیابی بکاپ فقط برای مدیر مجاز است.", 403);
    }

    const data = await bodyJson(request);
    if (String(data.confirm || "") !== "RESTORE") {
      return error(
        "restore_confirmation_required",
        "برای بازیابی باید تایید RESTORE ارسال شود.",
        409
      );
    }

    const snapshotId = decodeURIComponent(backupRestore[1]);
    try {
      const result = await restoreBackupSnapshot(env, user, request, snapshotId);
      return json({ ok: true, ...result });
    } catch (e) {
      if (e && e.code === "backup_not_found") {
        return error("backup_not_found", "بکاپ آماده پیدا نشد.", 404);
      }
      if (e && e.code === "backup_integrity_failed") {
        return error(
          "backup_integrity_failed",
          "صحت نسخه پشتیبان تایید نشد و بازیابی متوقف شد.",
          409
        );
      }
      throw e;
    }
  }

  const backupDelete = path.match(/^\/api\/backups\/([^/]+)$/);
  if (backupDelete && method === "DELETE") {
    if (!requireRole(user, ["admin"])) {
      return error("forbidden", "حذف بکاپ فقط برای مدیر مجاز است.", 403);
    }

    const snapshotId = decodeURIComponent(backupDelete[1]);
    const existing = await env.DB.prepare(
      "SELECT id,kind,status FROM backup_snapshots WHERE id=?"
    ).bind(snapshotId).first();
    if (!existing) return error("backup_not_found", "بکاپ پیدا نشد.", 404);

    await env.DB.prepare("DELETE FROM backup_snapshots WHERE id=?").bind(snapshotId).run();
    await audit(env, user.id, "delete_backup", "backup_snapshot", null, {
      backup_id: snapshotId,
      kind: existing.kind
    });
    return json({ ok: true, deleted_backup_id: snapshotId });
  }

  if (path === "/api/audit" && method === "GET") {
    if (!(await hasPermission(env, user, "view_audit_log"))) {
      return error("forbidden", "مجوز مشاهده مرکز فعالیت‌ها فعال نیست.", 403);
    }

    const userId = Math.max(0, Number(url.searchParams.get("user_id") || 0));
    const category = String(url.searchParams.get("category") || "").trim().toLowerCase();
    const action = String(url.searchParams.get("action") || "").trim();
    const entityType = String(url.searchParams.get("entity_type") || "").trim();
    const date = String(url.searchParams.get("date") || "").trim();
    const q = String(url.searchParams.get("q") || "").trim().slice(0, 120);
    const beforeId = Math.max(0, Number(url.searchParams.get("before_id") || 0));
    const limit = Math.min(150, Math.max(1, Number(url.searchParams.get("limit") || 60)));

    if (category && ![
      "sales","finance","inventory","catalog","customers","security","system"
    ].includes(category)) {
      return error("invalid_category", "دسته فعالیت معتبر نیست.");
    }

    if (date && !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return error("invalid_date", "تاریخ فیلتر معتبر نیست.");
    }

    const where = [];
    const binds = [];

    if (userId > 0) {
      where.push("a.user_id=?");
      binds.push(userId);
    }
    if (action) {
      where.push("a.action=?");
      binds.push(action);
    }
    if (entityType) {
      where.push("a.entity_type=?");
      binds.push(entityType);
    }
    if (date) {
      where.push("date(a.created_at,'+3 hours','+30 minutes')=date(?)");
      binds.push(date);
    }
    if (q) {
      const like = "%" + q + "%";
      where.push(
        "(a.action LIKE ? OR a.entity_type LIKE ? OR a.details LIKE ? OR u.name LIKE ? OR u.username LIKE ?)"
      );
      binds.push(like,like,like,like,like);
    }

    const categoryActions = {
      sales:[
        "open_order","add_order_item","update_order_item_qty","delete_order_item",
        "settle_order","reverse_settlement","cancel_order","merge_orders",
        "transfer_order","hard_delete_order"
      ],
      finance:[
        "open_shift","close_shift","create_expense","customer_payment",
        "customer_ledger_adjustment"
      ],
      inventory:[
        "create_inventory_item","update_inventory_item","inventory_movement",
        "create_inventory_link","delete_inventory_link","update_recipe"
      ],
      catalog:[
        "create_catalog_category","update_catalog_category","create_catalog_item",
        "update_catalog_item","create_hookah"
      ],
      customers:["create_customer","update_customer"],
      security:["setup_user","login","create_user","update_user","update_role_permissions"],
      system:["create_table"]
    };

    if (category) {
      const actions = categoryActions[category] || [];
      if (category === "system") {
        const known = Object.entries(categoryActions)
          .filter(([key]) => key !== "system")
          .flatMap(([,items]) => items);
        where.push(
          "(a.action IN (" + actions.map(() => "?").join(",") + ")" +
          " OR a.action NOT IN (" + known.map(() => "?").join(",") + "))"
        );
        binds.push(...actions,...known);
      } else {
        where.push("a.action IN (" + actions.map(() => "?").join(",") + ")");
        binds.push(...actions);
      }
    }

    if (beforeId > 0) {
      where.push("a.id<?");
      binds.push(beforeId);
    }

    const whereSql = where.length ? "WHERE " + where.join(" AND ") : "";

    const rows = await env.DB.prepare(
      `SELECT a.id,a.user_id,a.action,a.entity_type,a.entity_id,a.details,a.created_at,
              u.name AS user_name,u.username,u.role AS user_role
       FROM audit_logs a
       LEFT JOIN users u ON u.id=a.user_id
       ${whereSql}
       ORDER BY a.id DESC
       LIMIT ${limit}`
    ).bind(...binds).all();

    const logs = (rows.results || []).map((row) => ({
      ...row,
      category:auditCategory(row.action),
      severity:auditSeverity(row.action)
    }));

    const summaryWhere = [];
    const summaryBinds = [];
    if (userId > 0) {
      summaryWhere.push("a.user_id=?");
      summaryBinds.push(userId);
    }
    if (action) {
      summaryWhere.push("a.action=?");
      summaryBinds.push(action);
    }
    if (entityType) {
      summaryWhere.push("a.entity_type=?");
      summaryBinds.push(entityType);
    }
    if (date) {
      summaryWhere.push("date(a.created_at,'+3 hours','+30 minutes')=date(?)");
      summaryBinds.push(date);
    }
    if (q) {
      const like = "%" + q + "%";
      summaryWhere.push(
        "(a.action LIKE ? OR a.entity_type LIKE ? OR a.details LIKE ? OR u.name LIKE ? OR u.username LIKE ?)"
      );
      summaryBinds.push(like,like,like,like,like);
    }
    if (category) {
      const actions = categoryActions[category] || [];
      if (category === "system") {
        const known = Object.entries(categoryActions)
          .filter(([key]) => key !== "system")
          .flatMap(([,items]) => items);
        summaryWhere.push(
          "(a.action IN (" + actions.map(() => "?").join(",") + ")" +
          " OR a.action NOT IN (" + known.map(() => "?").join(",") + "))"
        );
        summaryBinds.push(...actions,...known);
      } else {
        summaryWhere.push("a.action IN (" + actions.map(() => "?").join(",") + ")");
        summaryBinds.push(...actions);
      }
    }

    const summarySql = summaryWhere.length ? "WHERE " + summaryWhere.join(" AND ") : "";
    const summary = await env.DB.prepare(
      `SELECT COUNT(*) AS matched_count,
              COUNT(DISTINCT a.user_id) AS active_users,
              COALESCE(SUM(CASE WHEN a.action IN (
                'hard_delete_order','reverse_settlement','update_role_permissions',
                'customer_ledger_adjustment'
              ) THEN 1 ELSE 0 END),0) AS critical_count
       FROM audit_logs a
       LEFT JOIN users u ON u.id=a.user_id
       ${summarySql}`
    ).bind(...summaryBinds).first();

    const today = await env.DB.prepare(
      `SELECT COUNT(*) AS count
       FROM audit_logs
       WHERE date(created_at,'+3 hours','+30 minutes')=date(?)`
    ).bind(iranDateKey()).first();

    const users = await env.DB.prepare(
      `SELECT DISTINCT u.id,u.username,u.name,u.role,u.active
       FROM audit_logs a
       JOIN users u ON u.id=a.user_id
       ORDER BY u.active DESC,u.name`
    ).all();

    const actions = await env.DB.prepare(
      `SELECT action,COUNT(*) AS count
       FROM audit_logs
       GROUP BY action
       ORDER BY count DESC,action
       LIMIT 100`
    ).all();

    const nextBeforeId = logs.length === limit
      ? Number(logs[logs.length - 1].id)
      : null;

    return json({
      ok:true,
      timezone:IRAN_TIME_ZONE,
      filters:{
        user_id:userId || null,
        category:category || null,
        action:action || null,
        entity_type:entityType || null,
        date:date || null,
        q:q || null
      },
      summary:{
        matched_count:Number(summary?.matched_count || 0),
        today_count:Number(today?.count || 0),
        active_users:Number(summary?.active_users || 0),
        critical_count:Number(summary?.critical_count || 0)
      },
      users:users.results || [],
      actions:actions.results || [],
      logs,
      next_before_id:nextBeforeId
    });
  }

  return error("not_found", "Route not found.", 404);
}

export default {
  async fetch(request, env) {
    try {
      return await routeWithOfflineIdempotency(request, env);
    } catch (e) {
      console.error(e);
      return error("internal_error", "Unexpected server error.", 500);
    }
  },

  async scheduled(controller, env, ctx) {
    ctx.waitUntil((async () => {
      try {
        const backup = await createBackupSnapshot(
          env,
          null,
          "daily",
          "automatic_daily",
          {
            trigger: controller && controller.cron ? controller.cron : "scheduled",
            iran_date: iranDateKey()
          }
        );
        await pruneAutomaticBackups(env);
        await audit(env, null, "scheduled_backup", "backup_snapshot", null, {
          backup_id: backup.id,
          row_count: Number(backup.row_count || 0),
          size_bytes: Number(backup.size_bytes || 0)
        });
      } catch (e) {
        console.error("scheduled_backup_failed", e);
      }
    })());
  },
};
