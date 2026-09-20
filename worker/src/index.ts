// full-debug-trigger: order-management-e2e
// setup-key-sync-trigger: configured
const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS",
  "access-control-allow-headers": "content-type,authorization,x-setup-key",
};

const encoder = new TextEncoder();
const PASSWORD_KDF_ITERATIONS = 5000;
const IRAN_TIME_ZONE = "Asia/Tehran";

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

  const salesTotal = totals.cash + totals.card + totals.transfer + totals.credit;
  const cashExpenses = Number(expenses?.cash_amount || 0);
  const expectedCash = Number(shift.opening_cash || 0) + totals.cash - cashExpenses;

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
    expenses_total: Number(expenses?.total || 0),
    cash_expenses: cashExpenses,
    expected_cash_live: expectedCash,
    settled_orders: Number(orders?.count || 0),
  };
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
    return json({ ok: true, name: "TraditionalCafe API", version: "1.0.0", health: "/api/health" });
  }

  if (path === "/api/health" && method === "GET") {
    const db = await env.DB.prepare("SELECT 1 AS ok").first();
    return json({
      ok: true,
      service: "TraditionalCafe API",
      database: db?.ok === 1 ? "ready" : "unknown",
      timezone: IRAN_TIME_ZONE,
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

    try {
      const result = await env.DB.prepare(
        "INSERT INTO users (username,name,role,pin_hash,pin_salt) VALUES (?,?,?,?,?)",
      ).bind(username, name, role, passwordHash, salt).run();

      const userId = Number(result.meta.last_row_id);
      const token = randomHex(32);
      const tokenHash = await sha256Hex(token);
      await env.DB.prepare(
        "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, datetime('now','+30 days'))",
      ).bind(userId, tokenHash).run();

      await audit(env, userId, "setup_user", "user", userId, { username, name, role });
      return json({
        ok: true,
        token,
        user: { id: userId, username, name, role },
        expires_in_days: 30,
      }, 201);
    } catch (e) {
      return error("user_exists", "این نام کاربری قبلاً ثبت شده است.", 409);
    }
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

    return json({
      ok: true,
      token,
      user: { id: user.id, username: user.username, name: user.name, role: user.role },
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
    return json({ ok: true, user });
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
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "مشاهده شیفت‌های همه کاربران فقط برای مدیر یا صندوق‌دار مجاز است.", 403);
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
      "SELECT id, username, name, role, active, created_at, updated_at FROM users ORDER BY id",
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
    if (!requireRole(user, ["admin"])) return error("forbidden", "Admin access required.", 403);
    const targetId = Number(userMatch[1]);
    const data = await bodyJson(request);
    const target = await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(targetId).first();
    if (!target) return error("not_found", "User not found.", 404);

    const username = data.username === undefined ? target.username : String(data.username).trim().toLowerCase();
    const name = data.name === undefined ? target.name : String(data.name).trim();
    const role = data.role === undefined ? target.role : String(data.role);
    const active = data.active === undefined ? Number(target.active) : (data.active ? 1 : 0);

    if (!/^[a-z0-9._-]{3,32}$/.test(username)) return error("invalid_username", "Invalid username.");
    if (!["admin","cashier","staff"].includes(role)) return error("invalid_role", "Invalid role.");

    if (data.password !== undefined) {
      const password = String(data.password);
      if (password.length < 8 || password.length > 128) return error("invalid_password", "Password must be at least 8 characters.");
      const salt = randomHex(16);
      let passwordHash;
      try {
        passwordHash = await hashPassword(password, salt);
      } catch (e) {
        console.error("update_user_password_hash_failed", e);
        return error("password_hash_failed", "خطا در پردازش امن رمز عبور.", 503);
      }
      await env.DB.prepare(
        "UPDATE users SET username=?, name=?, role=?, active=?, pin_hash=?, pin_salt=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
      ).bind(username, name, role, active, passwordHash, salt, targetId).run();
    } else {
      await env.DB.prepare(
        "UPDATE users SET username=?, name=?, role=?, active=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
      ).bind(username, name, role, active, targetId).run();
    }

    await audit(env, user.id, "update_user", "user", targetId, { username, name, role, active });
    return json({ ok: true });
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
      "cash_shifts"
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
      worker_version: "1.2.0",
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
    if (!requireRole(user, ["admin","cashier"])) return error("forbidden", "Insufficient access.", 403);
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


  if (path === "/api/catalog" && method === "GET") {
    const type = String(url.searchParams.get("type") || "").toLowerCase();
    const privileged = requireRole(user, ["admin","cashier"]);
    const includeAll = privileged && url.searchParams.get("all") === "1";
    const activeWhere = includeAll ? "" : " WHERE active=1";
    const columns = privileged
      ? "id, name, price, cost, active, created_at, updated_at"
      : "id, name, price, active, created_at, updated_at";

    if (type === "hookah") {
      const result = await env.DB.prepare(
        "SELECT " + columns + " FROM hookah_catalog" + activeWhere + " ORDER BY active DESC, name"
      ).all();
      return json({ ok: true, type: "hookah", items: result.results || [] });
    }

    if (type === "service") {
      const result = await env.DB.prepare(
        "SELECT " + columns + " FROM service_catalog" + activeWhere + " ORDER BY active DESC, name"
      ).all();
      return json({ ok: true, type: "service", items: result.results || [] });
    }

    const hookahs = await env.DB.prepare(
      "SELECT " + columns + " FROM hookah_catalog" + activeWhere + " ORDER BY active DESC, name"
    ).all();
    const services = await env.DB.prepare(
      "SELECT " + columns + " FROM service_catalog" + activeWhere + " ORDER BY active DESC, name"
    ).all();

    return json({
      ok: true,
      hookahs: hookahs.results || [],
      services: services.results || [],
    });
  }

  if (path === "/api/catalog" && method === "POST") {
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "تعریف منو فقط برای مدیر یا صندوق‌دار مجاز است.", 403);
    }
    const data = await bodyJson(request);
    const type = String(data.type || "").toLowerCase();
    const name = String(data.name || "").trim();
    const price = intAmount(data.price);
    const cost = intAmount(data.cost || 0);

    if (!["hookah","service"].includes(type)) {
      return error("invalid_type", "نوع باید قلیان یا خدمت باشد.");
    }
    if (!name || price === null || cost === null) {
      return error("invalid_input", "نام، قیمت فروش و هزینه تمام‌شده معتبر وارد کنید.");
    }

    const tableName = type === "hookah" ? "hookah_catalog" : "service_catalog";
    try {
      const result = await env.DB.prepare(
        `INSERT INTO ${tableName} (name, price, cost, active) VALUES (?,?,?,1)`
      ).bind(name, price, cost).run();

      const id = Number(result.meta.last_row_id);
      await audit(env, user.id, "create_catalog_item", type, id, { name, price, cost });
      return json({ ok: true, id, type, name, price, cost, active: 1 }, 201);
    } catch (e) {
      return error("catalog_exists", "موردی با این نام قبلاً تعریف شده است.", 409);
    }
  }

  const catalogItem = path.match(/^\/api\/catalog\/(hookah|service)\/(\d+)$/);
  if (catalogItem && method === "PATCH") {
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "ویرایش منو فقط برای مدیر یا صندوق‌دار مجاز است.", 403);
    }
    const type = catalogItem[1];
    const id = Number(catalogItem[2]);
    const tableName = type === "hookah" ? "hookah_catalog" : "service_catalog";
    const current = await env.DB.prepare(
      `SELECT id, name, price, cost, active FROM ${tableName} WHERE id=?`
    ).bind(id).first();
    if (!current) return error("not_found", "مورد تعریف‌شده پیدا نشد.", 404);

    const data = await bodyJson(request);
    const name = data.name === undefined ? current.name : String(data.name).trim();
    const price = data.price === undefined ? Number(current.price) : intAmount(data.price);
    const cost = data.cost === undefined ? Number(current.cost) : intAmount(data.cost);
    const active = data.active === undefined ? Number(current.active) : (data.active ? 1 : 0);

    if (!name || price === null || cost === null) {
      return error("invalid_input", "اطلاعات واردشده معتبر نیست.");
    }

    try {
      await env.DB.prepare(
        `UPDATE ${tableName}
         SET name=?, price=?, cost=?, active=?, updated_at=CURRENT_TIMESTAMP
         WHERE id=?`
      ).bind(name, price, cost, active, id).run();

      await audit(env, user.id, "update_catalog_item", type, id, { name, price, cost, active });
      return json({ ok: true, id, type, name, price, cost, active });
    } catch (e) {
      return error("catalog_exists", "موردی با این نام قبلاً تعریف شده است.", 409);
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

    if (user.role === "staff") {
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
      scope: user.role === "staff" ? "self_today" : "all",
      orders: result.results || [],
    });
  }

  const orderItems = path.match(/^\/api\/orders\/(\d+)\/items$/);
  if (orderItems && method === "POST") {
    const orderId = Number(orderItems[1]);
    const order = await env.DB.prepare(
      "SELECT id, status, opened_by FROM orders WHERE id=?"
    ).bind(orderId).first();
    if (!order || order.status !== "open") {
      return error("order_not_open", "Open order not found.", 404);
    }
    if (user.role === "staff" && Number(order.opened_by) !== Number(user.id)) {
      return error("forbidden", "شاگرد فقط می‌تواند سفارش‌های خودش را مدیریت کند.", 403);
    }

    const data = await bodyJson(request);
    const qty = Math.max(1, Math.round(Number(data.qty || 1)));

    let type;
    let name;
    let unitPrice;
    let unitCost;

    const catalogType = String(data.catalog_type || "").toLowerCase();
    const catalogId = Number(data.catalog_id || 0);

    if (["hookah","service"].includes(catalogType) && catalogId > 0) {
      const catalogTable = catalogType === "hookah" ? "hookah_catalog" : "service_catalog";
      const item = await env.DB.prepare(
        `SELECT id, name, price, cost, active FROM ${catalogTable} WHERE id=?`
      ).bind(catalogId).first();
      if (!item || Number(item.active) !== 1) {
        return error("catalog_item_not_found", "این مورد در منوی فعال پیدا نشد.", 404);
      }
      type = catalogType;
      name = item.name;
      unitPrice = Number(item.price);
      unitCost = Number(item.cost);
    } else {
      if (user.role === "staff") {
        return error("catalog_required", "شاگرد فقط می‌تواند از قلیان‌ها و خدمات تعریف‌شده فروش ثبت کند.", 403);
      }
      type = ["hookah","item","service"].includes(data.item_type) ? data.item_type : "item";
      name = String(data.name || "").trim();
      unitPrice = intAmount(data.unit_price);
      unitCost = intAmount(data.unit_cost || 0);
      if (!name || unitPrice === null || unitCost === null) {
        return error("invalid_input", "Invalid order item.");
      }
    }

    const result = await env.DB.prepare(
      "INSERT INTO order_items (order_id,item_type,name,qty,unit_price,unit_cost,created_by) VALUES (?,?,?,?,?,?,?)"
    ).bind(orderId, type, name, qty, unitPrice, unitCost, user.id).run();

    const totals = await recalcOrder(env, orderId);
    await audit(env, user.id, "add_order_item", "order_item", Number(result.meta.last_row_id), {
      order_id: orderId, name, qty
    });
    return json({ ok: true, id: result.meta.last_row_id, totals }, 201);
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
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "برگرداندن تسویه فقط برای مدیر یا صندوق‌دار مجاز است.", 403);
    }

    const orderId = Number(reverseSettlement[1]);
    const current = await env.DB.prepare(
      "SELECT id, status, total, table_id, closed_at FROM orders WHERE id=?"
    ).bind(orderId).first();
    if (!current || current.status !== "settled") {
      return error("not_settled", "این سفارش در وضعیت تسویه‌شده نیست.", 409);
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

    await env.DB.batch([
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
    ]);

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
          "SELECT id, order_id, item_type, name, qty, unit_price, created_by, created_at FROM order_items WHERE order_id=? ORDER BY id"
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

    const itemIds = await env.DB.prepare(
      "SELECT id FROM order_items WHERE order_id=?"
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

    const statements = [
      env.DB.prepare("DELETE FROM customer_ledger WHERE order_id=?").bind(orderId),
      env.DB.prepare("DELETE FROM payments WHERE order_id=?").bind(orderId),
    ];

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

    return json({ ok: true, deleted_order_id: orderId });
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
    if (user.role === "staff" && discount > 0) {
      return error("forbidden", "ثبت تخفیف فقط برای مدیر یا صندوق‌دار مجاز است.", 403);
    }

    await env.DB.prepare("UPDATE orders SET discount=? WHERE id=?").bind(discount, orderId).run();
    const totals = await recalcOrder(env, orderId);
    const payments = Array.isArray(data.payments) ? data.payments : [];
    if (totals.total > 0 && payments.length === 0) return error("payments_required", "Payment is required.");

    let paid = 0;
    const normalized = [];
    for (const p of payments) {
      const methodName = String(p.method || "");
      const amount = intAmount(p.amount);
      if (!["cash","card","transfer","credit"].includes(methodName) || !amount || amount <= 0) {
        return error("invalid_payment", "Invalid payment item.");
      }
      const customerId = p.customer_id ? Number(p.customer_id) : null;
      if (methodName === "credit" && !customerId) return error("customer_required", "Credit payment requires a customer.");
      if (customerId) {
        const customer = await env.DB.prepare("SELECT id FROM customers WHERE id=? AND active=1").bind(customerId).first();
        if (!customer) return error("customer_not_found", "Customer not found.", 404);
      }
      paid += amount;
      normalized.push({ method: methodName, amount, customer_id: customerId });
    }

    if (paid !== totals.total) {
      return error("payment_mismatch", "Payment total must equal order total.", 409, { expected: totals.total, received: paid });
    }

    const statements = normalized.map((p) =>
      env.DB.prepare(
        "INSERT INTO payments (order_id,method,amount,customer_id,created_by,shift_id) VALUES (?,?,?,?,?,?)",
      ).bind(orderId, p.method, p.amount, p.customer_id, user.id, shift.id)
    );
    if (statements.length) await env.DB.batch(statements);

    for (const p of normalized) {
      if (p.method === "credit") {
        await env.DB.prepare(
          "INSERT INTO customer_ledger (customer_id,order_id,entry_type,amount,note,created_by) VALUES (?,?,?,?,?,?)",
        ).bind(p.customer_id, orderId, "debt", p.amount, "نسیه سفارش", user.id).run();
      }
    }

    await env.DB.prepare(
      "UPDATE orders SET status='settled', closed_by=?, settled_shift_id=?, closed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=?",
    ).bind(user.id, shift.id, orderId).run();
    await audit(env, user.id, "settle_order", "order", orderId, { total: totals.total, payments: normalized });
    return json({ ok: true, order_id: orderId, totals, payments: normalized });
  }

  if (path === "/api/customers" && method === "GET") {
    const result = await env.DB.prepare(
      `SELECT c.id, c.name, c.phone, c.notes, c.active, c.created_at,
              COALESCE(SUM(l.amount),0) AS balance
       FROM customers c
       LEFT JOIN customer_ledger l ON l.customer_id=c.id
       WHERE c.active=1
       GROUP BY c.id
       ORDER BY c.name`,
    ).all();
    return json({ ok: true, customers: result.results || [] });
  }

  if (path === "/api/customers" && method === "POST") {
    const data = await bodyJson(request);
    const name = String(data.name || "").trim();
    if (!name) return error("invalid_name", "Customer name is required.");
    const result = await env.DB.prepare(
      "INSERT INTO customers (name,phone,notes) VALUES (?,?,?)",
    ).bind(name, data.phone ? String(data.phone) : null, data.notes ? String(data.notes) : null).run();
    await audit(env, user.id, "create_customer", "customer", Number(result.meta.last_row_id), { name });
    return json({ ok: true, id: result.meta.last_row_id }, 201);
  }

  const customerLedger = path.match(/^\/api\/customers\/(\d+)\/ledger$/);
  if (customerLedger && method === "GET") {
    const customerId = Number(customerLedger[1]);
    const customer = await env.DB.prepare(
      `SELECT c.*, COALESCE(SUM(l.amount),0) AS balance
       FROM customers c LEFT JOIN customer_ledger l ON l.customer_id=c.id
       WHERE c.id=? GROUP BY c.id`,
    ).bind(customerId).first();
    if (!customer) return error("not_found", "Customer not found.", 404);
    const entries = await env.DB.prepare(
      "SELECT * FROM customer_ledger WHERE customer_id=? ORDER BY id DESC LIMIT 200",
    ).bind(customerId).all();
    return json({ ok: true, customer, entries: entries.results || [] });
  }

  const customerPayment = path.match(/^\/api\/customers\/(\d+)\/payment$/);
  if (customerPayment && method === "POST") {
    const customerId = Number(customerPayment[1]);
    const customer = await env.DB.prepare("SELECT id FROM customers WHERE id=? AND active=1").bind(customerId).first();
    if (!customer) return error("not_found", "Customer not found.", 404);
    const data = await bodyJson(request);
    const amount = intAmount(data.amount);
    if (!amount || amount <= 0) return error("invalid_amount", "Amount must be greater than zero.");
    await env.DB.prepare(
      "INSERT INTO customer_ledger (customer_id,entry_type,amount,note,created_by) VALUES (?,?,?,?,?)",
    ).bind(customerId, "payment", -amount, data.note ? String(data.note) : "پرداخت بدهی", user.id).run();
    await audit(env, user.id, "customer_payment", "customer", customerId, { amount });
    return json({ ok: true });
  }

  if (path === "/api/expenses" && method === "GET") {
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "هزینه‌ها فقط برای مدیر یا صندوق‌دار قابل مشاهده است.", 403);
    }
    const result = await env.DB.prepare(
      `SELECT e.*, u.name AS created_by_name
       FROM expenses e JOIN users u ON u.id=e.created_by
       ORDER BY e.id DESC LIMIT 200`,
    ).all();
    return json({ ok: true, expenses: result.results || [] });
  }

  if (path === "/api/expenses" && method === "POST") {
    if (!requireRole(user, ["admin","cashier"])) {
      return error("forbidden", "ثبت هزینه فقط برای مدیر یا صندوق‌دار مجاز است.", 403);
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

  if (path === "/api/reports/summary" && method === "GET") {
    if (!requireRole(user, ["admin","cashier"])) return error("forbidden", "Insufficient access.", 403);
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

  if (path === "/api/audit" && method === "GET") {
    if (!requireRole(user, ["admin"])) return error("forbidden", "Admin access required.", 403);
    const result = await env.DB.prepare(
      `SELECT a.*, u.name AS user_name
       FROM audit_logs a LEFT JOIN users u ON u.id=a.user_id
       ORDER BY a.id DESC LIMIT 300`,
    ).all();
    return json({ ok: true, logs: result.results || [] });
  }

  return error("not_found", "Route not found.", 404);
}

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (e) {
      console.error(e);
      return error("internal_error", "Unexpected server error.", 500);
    }
  },
};
