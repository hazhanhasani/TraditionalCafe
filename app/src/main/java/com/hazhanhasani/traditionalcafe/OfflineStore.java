package com.hazhanhasani.traditionalcafe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfflineStore {

    private static final String DB_NAME = "traditionalcafe_offline.db";
    private static final int DB_VERSION = 1;
    private static Helper helper;

    private OfflineStore() {}

    private static synchronized Helper helper(Context context) {
        if (helper == null) {
            helper = new Helper(context.getApplicationContext());
        }
        return helper;
    }

    private static SQLiteDatabase db(Context context) {
        return helper(context).getWritableDatabase();
    }

    private static String currentUser(Context context) {
        String username = context
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("username", "");
        if (username == null || username.trim().isEmpty()) return "_anonymous";
        return username.trim().toLowerCase(Locale.US);
    }

    private static String cacheKey(Context context, String path) {
        return currentUser(context) + "|" + path;
    }

    public static synchronized void putCache(
            Context context,
            String path,
            JSONObject payload
    ) {
        if (path == null || payload == null) return;

        ContentValues values = new ContentValues();
        values.put("cache_key", cacheKey(context, path));
        values.put("username", currentUser(context));
        values.put("path", path);
        values.put("payload", payload.toString());
        values.put("updated_at", System.currentTimeMillis());

        db(context).insertWithOnConflict(
                "response_cache",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public static synchronized JSONObject getCache(
            Context context,
            String path
    ) {
        Cursor cursor = db(context).query(
                "response_cache",
                new String[]{"payload", "updated_at"},
                "cache_key=?",
                new String[]{cacheKey(context, path)},
                null,
                null,
                null,
                "1"
        );

        try {
            if (!cursor.moveToFirst()) return null;
            JSONObject payload = new JSONObject(cursor.getString(0));
            payload.put("_offline_cache", true);
            payload.put("_offline_cached_at", cursor.getLong(1));
            return payload;
        } catch (Exception ignored) {
            return null;
        } finally {
            cursor.close();
        }
    }

    public static synchronized long newestCacheTime(Context context) {
        Cursor cursor = db(context).rawQuery(
                "SELECT COALESCE(MAX(updated_at),0) FROM response_cache WHERE username=?",
                new String[]{currentUser(context)}
        );
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    public static synchronized int cacheCount(Context context) {
        Cursor cursor = db(context).rawQuery(
                "SELECT COUNT(*) FROM response_cache WHERE username=?",
                new String[]{currentUser(context)}
        );
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public static synchronized void clearCache(Context context) {
        db(context).delete(
                "response_cache",
                "username=?",
                new String[]{currentUser(context)}
        );
    }

    public static synchronized String enqueue(
            Context context,
            String operationId,
            String method,
            String path,
            JSONObject body
    ) {
        String id = operationId == null || operationId.trim().isEmpty()
                ? "op-" + UUID.randomUUID()
                : operationId;

        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("username", currentUser(context));
        values.put("method", method);
        values.put("path", path);
        values.put("body", body == null ? "{}" : body.toString());
        values.put("created_at", System.currentTimeMillis());
        values.put("attempts", 0);
        values.put("last_error", "");
        values.put("state", "pending");

        db(context).insertWithOnConflict(
                "pending_operations",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );

        return id;
    }

    public static synchronized List<PendingOperation> pendingOperations(
            Context context
    ) {
        return operationsByState(context, "pending");
    }

    public static synchronized List<PendingOperation> failedOperations(
            Context context
    ) {
        return operationsByState(context, "failed");
    }

    private static List<PendingOperation> operationsByState(
            Context context,
            String state
    ) {
        List<PendingOperation> rows = new ArrayList<>();
        Cursor cursor = db(context).query(
                "pending_operations",
                new String[]{
                        "id", "method", "path", "body", "created_at",
                        "attempts", "last_error", "state"
                },
                "username=? AND state=?",
                new String[]{currentUser(context), state},
                null,
                null,
                "created_at ASC"
        );

        try {
            while (cursor.moveToNext()) {
                rows.add(new PendingOperation(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getInt(5),
                        cursor.getString(6),
                        cursor.getString(7)
                ));
            }
        } finally {
            cursor.close();
        }

        return rows;
    }

    public static synchronized void markOperationDone(
            Context context,
            String id
    ) {
        db(context).delete(
                "pending_operations",
                "id=? AND username=?",
                new String[]{id, currentUser(context)}
        );
    }

    public static synchronized void markOperationRetry(
            Context context,
            String id,
            String error
    ) {
        ContentValues values = new ContentValues();
        values.put("attempts", operationAttempts(context, id) + 1);
        values.put("last_error", safeError(error));
        values.put("state", "pending");

        db(context).update(
                "pending_operations",
                values,
                "id=? AND username=?",
                new String[]{id, currentUser(context)}
        );
    }

    public static synchronized void markOperationFailed(
            Context context,
            String id,
            String error
    ) {
        ContentValues values = new ContentValues();
        values.put("attempts", operationAttempts(context, id) + 1);
        values.put("last_error", safeError(error));
        values.put("state", "failed");

        db(context).update(
                "pending_operations",
                values,
                "id=? AND username=?",
                new String[]{id, currentUser(context)}
        );
    }

    public static synchronized void retryFailedOperations(Context context) {
        ContentValues values = new ContentValues();
        values.put("state", "pending");
        values.put("last_error", "");

        db(context).update(
                "pending_operations",
                values,
                "username=? AND state='failed'",
                new String[]{currentUser(context)}
        );

        ContentValues draftValues = new ContentValues();
        draftValues.put("state", "pending");
        draftValues.put("last_error", "");

        db(context).update(
                "draft_orders",
                draftValues,
                "username=? AND state='failed'",
                new String[]{currentUser(context)}
        );
    }

    private static int operationAttempts(Context context, String id) {
        Cursor cursor = db(context).rawQuery(
                "SELECT attempts FROM pending_operations WHERE id=? AND username=? LIMIT 1",
                new String[]{id, currentUser(context)}
        );
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public static synchronized int pendingCount(Context context) {
        return countOperations(context, "pending");
    }

    public static synchronized int failedCount(Context context) {
        return countOperations(context, "failed") + failedDraftCount(context);
    }

    private static int countOperations(Context context, String state) {
        Cursor cursor = db(context).rawQuery(
                "SELECT COUNT(*) FROM pending_operations WHERE username=? AND state=?",
                new String[]{currentUser(context), state}
        );
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public static synchronized long createDraftOrder(
            Context context,
            long tableId
    ) throws Exception {
        JSONObject shiftCache = getCache(context, "/api/shifts/current");
        if (
                shiftCache == null ||
                shiftCache.isNull("shift") ||
                shiftCache.optJSONObject("shift") == null
        ) {
            throw new IllegalStateException(
                    "برای ثبت سفارش آفلاین باید قبل از قطع اینترنت یک شیفت باز و همگام‌شده داشته باشید."
            );
        }

        JSONObject tables = getCache(context, "/api/tables");
        JSONObject table = null;
        if (tables != null) {
            JSONArray list = tables.optJSONArray("tables");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject candidate = list.optJSONObject(i);
                    if (
                            candidate != null &&
                            candidate.optLong("id", 0L) == tableId
                    ) {
                        table = candidate;
                        break;
                    }
                }
            }
        }

        if (table == null) {
            throw new IllegalStateException(
                    "اطلاعات این میز در حافظه آفلاین موجود نیست."
            );
        }

        if (
                table.optInt("busy", 0) == 1 ||
                table.optLong("order_id", 0L) > 0L
        ) {
            throw new IllegalStateException(
                    "طبق آخرین همگام‌سازی این میز سفارش باز دارد؛ برای جلوگیری از تداخل، ساخت سفارش آفلاین جدید روی آن مجاز نیست."
            );
        }

        Cursor existing = db(context).rawQuery(
                "SELECT id FROM draft_orders WHERE username=? AND table_id=? AND state IN ('pending','failed') ORDER BY id DESC LIMIT 1",
                new String[]{currentUser(context), String.valueOf(tableId)}
        );
        try {
            if (existing.moveToFirst()) return -existing.getLong(0);
        } finally {
            existing.close();
        }

        ContentValues values = new ContentValues();
        values.put("username", currentUser(context));
        values.put("table_id", tableId);
        values.put("table_name", table.optString("name", "میز"));
        values.put("operation_id", "draft-" + UUID.randomUUID());
        values.put("created_at", System.currentTimeMillis());
        values.put("updated_at", System.currentTimeMillis());
        values.put("state", "pending");
        values.put("last_error", "");

        long id = db(context).insertOrThrow("draft_orders", null, values);
        return -id;
    }

    public static synchronized long resolveServerOrderId(
            Context context,
            long localOrderId
    ) {
        if (localOrderId >= 0) return localOrderId;

        long draftId = Math.abs(localOrderId);
        Cursor cursor = db(context).rawQuery(
                "SELECT server_order_id FROM draft_orders WHERE id=? AND username=? LIMIT 1",
                new String[]{String.valueOf(draftId), currentUser(context)}
        );
        try {
            if (!cursor.moveToFirst() || cursor.isNull(0)) return 0L;
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    public static synchronized JSONObject localOrderResponse(
            Context context,
            long localOrderId
    ) {
        if (localOrderId >= 0) return null;

        long draftId = Math.abs(localOrderId);
        Cursor orderCursor = db(context).rawQuery(
                "SELECT table_id,table_name,created_at,state,last_error FROM draft_orders WHERE id=? AND username=? LIMIT 1",
                new String[]{String.valueOf(draftId), currentUser(context)}
        );

        try {
            if (!orderCursor.moveToFirst()) return null;

            JSONObject order = new JSONObject();
            order.put("id", localOrderId);
            order.put("table_id", orderCursor.getLong(0));
            order.put("table_name", orderCursor.getString(1));
            order.put("status", "open");
            order.put("opened_at", sqliteUtc(orderCursor.getLong(2)));
            order.put("offline_local", true);
            order.put("offline_state", orderCursor.getString(3));
            order.put("offline_error", orderCursor.getString(4));

            JSONArray items = draftItems(context, draftId);
            long subtotal = calculateSubtotal(items);
            order.put("subtotal", subtotal);
            order.put("discount", 0);
            order.put("total", subtotal);

            JSONObject response = new JSONObject();
            response.put("ok", true);
            response.put("order", order);
            response.put("items", items);
            response.put("payments", new JSONArray());
            response.put("_offline_cache", true);
            response.put("_offline_local_order", true);
            response.put("_offline_cached_at", System.currentTimeMillis());
            return response;
        } catch (Exception ignored) {
            return null;
        } finally {
            orderCursor.close();
        }
    }

    public static synchronized JSONObject addDraftItem(
            Context context,
            long localOrderId,
            JSONObject body
    ) throws Exception {
        long draftId = requireDraftId(localOrderId);
        JSONObject catalog = resolveCatalogItem(context, body);

        int qty = Math.max(1, body.optInt("qty", 1));

        ContentValues values = new ContentValues();
        values.put("draft_id", draftId);
        values.put("catalog_type", catalog.optString("catalog_type", "service"));
        if (catalog.optLong("catalog_id", 0L) > 0L) {
            values.put("catalog_id", catalog.optLong("catalog_id"));
        }
        values.put("name", catalog.optString("name", "مورد"));
        values.put("qty", qty);
        values.put("unit_price", catalog.optLong("unit_price", 0L));
        values.put("created_at", System.currentTimeMillis());

        long itemId = db(context).insertOrThrow("draft_items", null, values);
        touchDraft(context, draftId);

        JSONObject response = localOrderResponse(context, localOrderId);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("id", -itemId);
        result.put("offline", true);
        result.put("queued", true);
        if (response != null) {
            JSONObject order = response.optJSONObject("order");
            if (order != null) {
                JSONObject totals = new JSONObject();
                totals.put("subtotal", order.optLong("subtotal", 0L));
                totals.put("discount", 0);
                totals.put("total", order.optLong("total", 0L));
                result.put("totals", totals);
            }
        }
        return result;
    }

    public static synchronized JSONObject patchDraftItem(
            Context context,
            long localOrderId,
            long localItemId,
            JSONObject body
    ) throws Exception {
        long draftId = requireDraftId(localOrderId);
        long itemId = Math.abs(localItemId);
        int qty = body.optInt("qty", 0);
        if (qty <= 0 || qty > 999) {
            throw new IllegalArgumentException("تعداد باید بیشتر از صفر باشد.");
        }

        ContentValues values = new ContentValues();
        values.put("qty", qty);

        int changed = db(context).update(
                "draft_items",
                values,
                "id=? AND draft_id=?",
                new String[]{String.valueOf(itemId), String.valueOf(draftId)}
        );
        if (changed == 0) throw new IllegalStateException("آیتم آفلاین پیدا نشد.");

        touchDraft(context, draftId);

        JSONObject response = localOrderResponse(context, localOrderId);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("item_id", localItemId);
        result.put("qty", qty);
        result.put("offline", true);
        if (response != null) {
            JSONObject order = response.optJSONObject("order");
            if (order != null) result.put("totals", totalsFromOrder(order));
        }
        return result;
    }

    public static synchronized JSONObject deleteDraftItem(
            Context context,
            long localOrderId,
            long localItemId
    ) throws Exception {
        long draftId = requireDraftId(localOrderId);
        long itemId = Math.abs(localItemId);

        db(context).delete(
                "draft_items",
                "id=? AND draft_id=?",
                new String[]{String.valueOf(itemId), String.valueOf(draftId)}
        );
        touchDraft(context, draftId);

        JSONObject response = localOrderResponse(context, localOrderId);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("item_id", localItemId);
        result.put("offline", true);
        if (response != null) {
            JSONObject order = response.optJSONObject("order");
            if (order != null) result.put("totals", totalsFromOrder(order));
        }
        return result;
    }

    public static synchronized List<DraftOrder> syncableDrafts(Context context) {
        List<DraftOrder> result = new ArrayList<>();

        Cursor cursor = db(context).rawQuery(
                "SELECT id,table_id,table_name,operation_id,created_at FROM draft_orders WHERE username=? AND state='pending' AND server_order_id IS NULL ORDER BY created_at ASC",
                new String[]{currentUser(context)}
        );

        try {
            while (cursor.moveToNext()) {
                long draftId = cursor.getLong(0);
                JSONArray items = draftItems(context, draftId);
                if (items.length() == 0) continue;

                JSONObject payload = new JSONObject();
                try {
                    payload.put("local_order_id", -draftId);
                    payload.put("operation_id", cursor.getString(3));
                    payload.put("table_id", cursor.getLong(1));
                    payload.put("offline_created_at", sqliteUtc(cursor.getLong(4)));

                    JSONArray syncItems = new JSONArray();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        JSONObject sync = new JSONObject();
                        sync.put("catalog_type", item.optString("catalog_kind", "service"));
                        sync.put("catalog_id", item.optLong("catalog_id", 0L));
                        sync.put("qty", item.optInt("qty", 1));
                        syncItems.put(sync);
                    }
                    payload.put("items", syncItems);
                } catch (Exception ignored) {
                    continue;
                }

                result.add(new DraftOrder(
                        draftId,
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        payload
                ));
            }
        } finally {
            cursor.close();
        }

        return result;
    }

    public static synchronized void markDraftSynced(
            Context context,
            long draftId,
            long serverOrderId
    ) {
        ContentValues values = new ContentValues();
        values.put("server_order_id", serverOrderId);
        values.put("state", "synced");
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());

        db(context).update(
                "draft_orders",
                values,
                "id=? AND username=?",
                new String[]{String.valueOf(draftId), currentUser(context)}
        );
    }

    public static synchronized void markDraftFailed(
            Context context,
            long draftId,
            String error
    ) {
        ContentValues values = new ContentValues();
        values.put("state", "failed");
        values.put("last_error", safeError(error));
        values.put("updated_at", System.currentTimeMillis());

        db(context).update(
                "draft_orders",
                values,
                "id=? AND username=?",
                new String[]{String.valueOf(draftId), currentUser(context)}
        );
    }

    public static synchronized int pendingDraftCount(Context context) {
        return draftStateCount(context, "pending");
    }

    private static int failedDraftCount(Context context) {
        return draftStateCount(context, "failed");
    }

    private static int draftStateCount(Context context, String state) {
        Cursor cursor = db(context).rawQuery(
                "SELECT COUNT(*) FROM draft_orders WHERE username=? AND state=? AND server_order_id IS NULL",
                new String[]{currentUser(context), state}
        );
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public static synchronized List<DraftSummary> draftSummaries(Context context) {
        List<DraftSummary> rows = new ArrayList<>();
        Cursor cursor = db(context).rawQuery(
                "SELECT id,table_name,created_at,state,last_error,server_order_id FROM draft_orders WHERE username=? AND state IN ('pending','failed') ORDER BY created_at ASC",
                new String[]{currentUser(context)}
        );

        try {
            while (cursor.moveToNext()) {
                rows.add(new DraftSummary(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.isNull(5) ? 0L : cursor.getLong(5)
                ));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    public static synchronized void applyOptimisticOrderMutation(
            Context context,
            String method,
            String path,
            JSONObject body
    ) {
        try {
            Pattern addPattern = Pattern.compile("^/api/orders/(\\d+)/items$");
            Pattern itemPattern = Pattern.compile("^/api/orders/(\\d+)/items/(\\d+)$");

            Matcher add = addPattern.matcher(path);
            Matcher item = itemPattern.matcher(path);

            if (add.matches() && "POST".equals(method)) {
                long orderId = Long.parseLong(add.group(1));
                String detailPath = "/api/orders/" + orderId;
                JSONObject cached = getCache(context, detailPath);
                if (cached == null) return;

                JSONObject catalog = resolveCatalogItem(context, body);
                JSONArray items = cached.optJSONArray("items");
                if (items == null) items = new JSONArray();

                JSONObject row = new JSONObject();
                row.put("id", -System.currentTimeMillis());
                row.put("order_id", orderId);
                row.put("catalog_id", catalog.optLong("catalog_id", 0L));
                row.put("catalog_kind", catalog.optString("catalog_type", "service"));
                row.put("item_type",
                        "hookah".equals(catalog.optString("catalog_type"))
                                ? "hookah"
                                : "service");
                row.put("name", catalog.optString("name", "مورد"));
                row.put("qty", Math.max(1, body.optInt("qty", 1)));
                row.put("unit_price", catalog.optLong("unit_price", 0L));
                row.put("created_at", sqliteUtc(System.currentTimeMillis()));

                items.put(row);
                cached.put("items", items);
                recalcCachedOrder(cached);
                stripOfflineMeta(cached);
                putCache(context, detailPath, cached);
                return;
            }

            if (item.matches()) {
                long orderId = Long.parseLong(item.group(1));
                long itemId = Long.parseLong(item.group(2));
                String detailPath = "/api/orders/" + orderId;
                JSONObject cached = getCache(context, detailPath);
                if (cached == null) return;

                JSONArray items = cached.optJSONArray("items");
                if (items == null) return;

                for (int i = 0; i < items.length(); i++) {
                    JSONObject row = items.optJSONObject(i);
                    if (row == null || row.optLong("id", 0L) != itemId) continue;

                    if ("DELETE".equals(method)) {
                        items.remove(i);
                    } else if ("PATCH".equals(method)) {
                        row.put("qty", Math.max(1, body.optInt("qty", row.optInt("qty", 1))));
                    }
                    break;
                }

                cached.put("items", items);
                recalcCachedOrder(cached);
                stripOfflineMeta(cached);
                putCache(context, detailPath, cached);
            }
        } catch (Exception ignored) {
        }
    }

    private static void recalcCachedOrder(JSONObject cached) throws Exception {
        JSONArray items = cached.optJSONArray("items");
        long subtotal = calculateSubtotal(items == null ? new JSONArray() : items);

        JSONObject order = cached.optJSONObject("order");
        if (order == null) return;

        long discount = order.optLong("discount", 0L);
        order.put("subtotal", subtotal);
        order.put("total", Math.max(0L, subtotal - discount));
        cached.put("order", order);
    }

    private static JSONObject totalsFromOrder(JSONObject order) throws Exception {
        JSONObject totals = new JSONObject();
        totals.put("subtotal", order.optLong("subtotal", 0L));
        totals.put("discount", order.optLong("discount", 0L));
        totals.put("total", order.optLong("total", 0L));
        return totals;
    }

    private static void stripOfflineMeta(JSONObject payload) {
        payload.remove("_offline_cache");
        payload.remove("_offline_cached_at");
    }

    private static long requireDraftId(long localOrderId) {
        if (localOrderId >= 0) {
            throw new IllegalArgumentException("شناسه سفارش آفلاین معتبر نیست.");
        }
        return Math.abs(localOrderId);
    }

    private static JSONObject resolveCatalogItem(
            Context context,
            JSONObject body
    ) throws Exception {
        String type = body.optString(
                "catalog_type",
                body.optString("catalog_kind", "service")
        ).toLowerCase(Locale.US);
        long catalogId = body.optLong("catalog_id", 0L);

        if (catalogId > 0L) {
            JSONObject cached = getCache(context, "/api/catalog?type=" + type);
            if (cached != null) {
                JSONArray items = cached.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (
                                item != null &&
                                item.optLong("id", 0L) == catalogId
                        ) {
                            JSONObject result = new JSONObject();
                            result.put("catalog_type", type);
                            result.put("catalog_id", catalogId);
                            result.put("name", item.optString("name", "مورد"));
                            result.put("unit_price", item.optLong("price", 0L));
                            return result;
                        }
                    }
                }
            }

            throw new IllegalStateException(
                    "این آیتم منو در حافظه آفلاین موجود نیست."
            );
        }

        String role = context
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("role", "staff");
        if ("staff".equals(role)) {
            throw new IllegalStateException(
                    "شاگرد در حالت آفلاین فقط می‌تواند از منوی ذخیره‌شده فروش ثبت کند."
            );
        }

        String name = body.optString("name", "").trim();
        long unitPrice = body.optLong("unit_price", -1L);
        if (name.isEmpty() || unitPrice < 0L) {
            throw new IllegalArgumentException("اطلاعات آیتم آفلاین معتبر نیست.");
        }

        JSONObject result = new JSONObject();
        result.put("catalog_type", type);
        result.put("catalog_id", 0L);
        result.put("name", name);
        result.put("unit_price", unitPrice);
        return result;
    }

    private static JSONArray draftItems(Context context, long draftId) {
        JSONArray items = new JSONArray();
        Cursor cursor = db(context).rawQuery(
                "SELECT id,catalog_type,catalog_id,name,qty,unit_price,created_at FROM draft_items WHERE draft_id=? ORDER BY id ASC",
                new String[]{String.valueOf(draftId)}
        );

        try {
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                try {
                    item.put("id", -cursor.getLong(0));
                    item.put("order_id", -draftId);
                    item.put("catalog_kind", cursor.getString(1));
                    item.put(
                            "item_type",
                            "hookah".equals(cursor.getString(1))
                                    ? "hookah"
                                    : "service"
                    );
                    if (!cursor.isNull(2)) {
                        item.put("catalog_id", cursor.getLong(2));
                    }
                    item.put("name", cursor.getString(3));
                    item.put("qty", cursor.getInt(4));
                    item.put("unit_price", cursor.getLong(5));
                    item.put("created_at", sqliteUtc(cursor.getLong(6)));
                    items.put(item);
                } catch (Exception ignored) {
                }
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private static long calculateSubtotal(JSONArray items) {
        long total = 0L;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            total += item.optLong("qty", 0L) * item.optLong("unit_price", 0L);
        }
        return total;
    }

    private static void touchDraft(Context context, long draftId) {
        ContentValues values = new ContentValues();
        values.put("updated_at", System.currentTimeMillis());
        values.put("state", "pending");
        values.put("last_error", "");

        db(context).update(
                "draft_orders",
                values,
                "id=? AND username=?",
                new String[]{String.valueOf(draftId), currentUser(context)}
        );
    }

    private static String safeError(String value) {
        if (value == null) return "";
        value = value.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static String sqliteUtc(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.US
        );
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMillis));
    }

    public static final class PendingOperation {
        public final String id;
        public final String method;
        public final String path;
        public final String body;
        public final long createdAt;
        public final int attempts;
        public final String lastError;
        public final String state;

        PendingOperation(
                String id,
                String method,
                String path,
                String body,
                long createdAt,
                int attempts,
                String lastError,
                String state
        ) {
            this.id = id;
            this.method = method;
            this.path = path;
            this.body = body;
            this.createdAt = createdAt;
            this.attempts = attempts;
            this.lastError = lastError == null ? "" : lastError;
            this.state = state;
        }

        public JSONObject bodyJson() {
            try {
                return new JSONObject(body == null ? "{}" : body);
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    public static final class DraftOrder {
        public final long id;
        public final long tableId;
        public final String tableName;
        public final String operationId;
        public final long createdAt;
        public final JSONObject payload;

        DraftOrder(
                long id,
                long tableId,
                String tableName,
                String operationId,
                long createdAt,
                JSONObject payload
        ) {
            this.id = id;
            this.tableId = tableId;
            this.tableName = tableName;
            this.operationId = operationId;
            this.createdAt = createdAt;
            this.payload = payload;
        }
    }

    public static final class DraftSummary {
        public final long id;
        public final String tableName;
        public final long createdAt;
        public final String state;
        public final String lastError;
        public final long serverOrderId;

        DraftSummary(
                long id,
                String tableName,
                long createdAt,
                String state,
                String lastError,
                long serverOrderId
        ) {
            this.id = id;
            this.tableName = tableName;
            this.createdAt = createdAt;
            this.state = state;
            this.lastError = lastError == null ? "" : lastError;
            this.serverOrderId = serverOrderId;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {

        Helper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            super.onConfigure(db);
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE response_cache (" +
                            "cache_key TEXT PRIMARY KEY," +
                            "username TEXT NOT NULL," +
                            "path TEXT NOT NULL," +
                            "payload TEXT NOT NULL," +
                            "updated_at INTEGER NOT NULL" +
                            ")"
            );
            db.execSQL(
                    "CREATE INDEX idx_response_cache_user ON response_cache(username,updated_at)"
            );

            db.execSQL(
                    "CREATE TABLE pending_operations (" +
                            "id TEXT PRIMARY KEY," +
                            "username TEXT NOT NULL," +
                            "method TEXT NOT NULL," +
                            "path TEXT NOT NULL," +
                            "body TEXT NOT NULL," +
                            "created_at INTEGER NOT NULL," +
                            "attempts INTEGER NOT NULL DEFAULT 0," +
                            "last_error TEXT NOT NULL DEFAULT ''," +
                            "state TEXT NOT NULL DEFAULT 'pending'" +
                            ")"
            );
            db.execSQL(
                    "CREATE INDEX idx_pending_operations_user ON pending_operations(username,state,created_at)"
            );

            db.execSQL(
                    "CREATE TABLE draft_orders (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "username TEXT NOT NULL," +
                            "table_id INTEGER NOT NULL," +
                            "table_name TEXT NOT NULL," +
                            "operation_id TEXT NOT NULL UNIQUE," +
                            "server_order_id INTEGER," +
                            "created_at INTEGER NOT NULL," +
                            "updated_at INTEGER NOT NULL," +
                            "state TEXT NOT NULL DEFAULT 'pending'," +
                            "last_error TEXT NOT NULL DEFAULT ''" +
                            ")"
            );
            db.execSQL(
                    "CREATE INDEX idx_draft_orders_user ON draft_orders(username,state,created_at)"
            );

            db.execSQL(
                    "CREATE TABLE draft_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "draft_id INTEGER NOT NULL," +
                            "catalog_type TEXT NOT NULL," +
                            "catalog_id INTEGER," +
                            "name TEXT NOT NULL," +
                            "qty INTEGER NOT NULL," +
                            "unit_price INTEGER NOT NULL," +
                            "created_at INTEGER NOT NULL," +
                            "FOREIGN KEY(draft_id) REFERENCES draft_orders(id) ON DELETE CASCADE" +
                            ")"
            );
            db.execSQL(
                    "CREATE INDEX idx_draft_items_order ON draft_items(draft_id,id)"
            );
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase db,
                int oldVersion,
                int newVersion
        ) {
        }
    }
}
