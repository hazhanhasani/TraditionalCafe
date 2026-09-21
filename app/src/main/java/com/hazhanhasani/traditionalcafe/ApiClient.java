package com.hazhanhasani.traditionalcafe;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiClient {

    public static final String BASE =
            "https://traditionalcafe.hazhanhasani4268-0f9.workers.dev";

    private static final Pattern LOCAL_ORDER_PATH =
            Pattern.compile("^/api/orders/(-\\d+)(.*)$");
    private static final Pattern TABLE_OPEN =
            Pattern.compile("^/api/tables/(\\d+)/open$");
    private static final Pattern LOCAL_ORDER_ITEMS =
            Pattern.compile("^/api/orders/(-\\d+)/items$");
    private static final Pattern LOCAL_ORDER_ITEM =
            Pattern.compile("^/api/orders/(-\\d+)/items/(-?\\d+)$");

    private ApiClient() {}

    public static JSONObject get(Context context, String path) throws Exception {
        String resolved = resolveMappedPath(context, path);

        Matcher local = LOCAL_ORDER_PATH.matcher(resolved);
        if (local.matches()) {
            long localOrderId = Long.parseLong(local.group(1));
            String suffix = local.group(2);

            if (suffix == null || suffix.isEmpty()) {
                JSONObject localResponse =
                        OfflineStore.localOrderResponse(context, localOrderId);
                if (localResponse != null) return localResponse;
            }
        }

        try {
            JSONObject response = requestOnline(
                    context,
                    "GET",
                    resolved,
                    null,
                    null
            );
            OfflineStore.putCache(context, resolved, response);
            OfflineSyncManager.markOnline(context);
            OfflineSyncManager.syncAsync(context);
            return response;
        } catch (ApiException e) {
            if (isTransient(e)) {
                OfflineSyncManager.markOffline(context);
                JSONObject cached = OfflineStore.getCache(context, resolved);
                if (cached != null) return cached;
            }
            throw e;
        }
    }

    public static JSONObject post(
            Context context,
            String path,
            JSONObject body
    ) throws Exception {
        JSONObject safeBody = body == null ? new JSONObject() : body;
        String resolved = resolveMappedPath(context, path);

        Matcher localItems = LOCAL_ORDER_ITEMS.matcher(resolved);
        if (localItems.matches()) {
            long localOrderId = Long.parseLong(localItems.group(1));
            return OfflineStore.addDraftItem(
                    context,
                    localOrderId,
                    safeBody
            );
        }

        Matcher localPath = LOCAL_ORDER_PATH.matcher(resolved);
        if (localPath.matches()) {
            long localOrderId = Long.parseLong(localPath.group(1));
            String suffix = localPath.group(2) == null
                    ? ""
                    : localPath.group(2);

            if ("/settle".equals(suffix)) {
                throw new ApiException(
                        0,
                        "offline_settlement_blocked",
                        "تسویه نهایی در حالت آفلاین انجام نمی‌شود. سفارش ذخیره شده و پس از اتصال، ابتدا همگام‌سازی و سپس تسویه را انجام دهید."
                );
            }

            throw new ApiException(
                    0,
                    "offline_action_blocked",
                    "این عملیات روی سفارش آفلاین تا زمان همگام‌سازی قابل انجام نیست."
            );
        }

        Matcher tableOpen = TABLE_OPEN.matcher(resolved);
        if (tableOpen.matches()) {
            try {
                JSONObject response = requestOnline(
                        context,
                        "POST",
                        resolved,
                        safeBody,
                        null
                );
                OfflineSyncManager.markOnline(context);
                OfflineSyncManager.syncAsync(context);
                return response;
            } catch (ApiException e) {
                if (!isTransient(e)) throw e;

                OfflineSyncManager.markOffline(context);
                long localOrderId = OfflineStore.createDraftOrder(
                        context,
                        Long.parseLong(tableOpen.group(1))
                );

                JSONObject queued = new JSONObject();
                queued.put("ok", true);
                queued.put("order_id", localOrderId);
                queued.put(
                        "table_id",
                        Long.parseLong(tableOpen.group(1))
                );
                queued.put("offline", true);
                queued.put("queued", true);
                return queued;
            }
        }

        return mutate(
                context,
                "POST",
                resolved,
                safeBody
        );
    }

    public static JSONObject patch(
            Context context,
            String path,
            JSONObject body
    ) throws Exception {
        JSONObject safeBody = body == null ? new JSONObject() : body;
        String resolved = resolveMappedPath(context, path);

        Matcher localItem = LOCAL_ORDER_ITEM.matcher(resolved);
        if (localItem.matches()) {
            return OfflineStore.patchDraftItem(
                    context,
                    Long.parseLong(localItem.group(1)),
                    Long.parseLong(localItem.group(2)),
                    safeBody
            );
        }

        return mutate(
                context,
                "PATCH",
                resolved,
                safeBody
        );
    }

    public static JSONObject delete(
            Context context,
            String path
    ) throws Exception {
        String resolved = resolveMappedPath(context, path);

        Matcher localItem = LOCAL_ORDER_ITEM.matcher(resolved);
        if (localItem.matches()) {
            return OfflineStore.deleteDraftItem(
                    context,
                    Long.parseLong(localItem.group(1)),
                    Long.parseLong(localItem.group(2))
            );
        }

        Matcher localPath = LOCAL_ORDER_PATH.matcher(resolved);
        if (localPath.matches()) {
            throw new ApiException(
                    0,
                    "offline_action_blocked",
                    "حذف یا لغو سفارش آفلاین قبل از همگام‌سازی مجاز نیست."
            );
        }

        return mutate(
                context,
                "DELETE",
                resolved,
                new JSONObject()
        );
    }

    private static JSONObject mutate(
            Context context,
            String method,
            String path,
            JSONObject body
    ) throws Exception {
        boolean queueable = isQueueable(method, path);
        String operationId = queueable
                ? "op-" + UUID.randomUUID()
                : null;

        try {
            JSONObject response = requestOnline(
                    context,
                    method,
                    path,
                    body,
                    operationId
            );

            afterSuccessfulMutation(
                    context,
                    method,
                    path,
                    body,
                    response
            );

            OfflineSyncManager.markOnline(context);
            OfflineSyncManager.syncAsync(context);
            return response;
        } catch (ApiException e) {
            if (!isTransient(e)) throw e;

            OfflineSyncManager.markOffline(context);

            if (!queueable) {
                throw new ApiException(
                        0,
                        "offline_action_blocked",
                        blockedMessage(path)
                );
            }

            OfflineStore.enqueue(
                    context,
                    operationId,
                    method,
                    path,
                    body
            );

            OfflineStore.applyOptimisticOrderMutation(
                    context,
                    method,
                    path,
                    body
            );

            OfflineSyncManager.broadcast(context);

            JSONObject queued = new JSONObject();
            queued.put("ok", true);
            queued.put("queued", true);
            queued.put("offline", true);
            queued.put("offline_operation_id", operationId);
            queued.put(
                    "message",
                    "عملیات در دستگاه ذخیره شد و پس از اتصال اینترنت همگام می‌شود."
            );
            return queued;
        }
    }

    private static void afterSuccessfulMutation(
            Context context,
            String method,
            String path,
            JSONObject body,
            JSONObject response
    ) {
        try {
            if (
                    "POST".equals(method) &&
                    "/api/shifts/open".equals(path)
            ) {
                OfflineStore.putCache(
                        context,
                        "/api/shifts/current",
                        response
                );
            }

            if (
                    "POST".equals(method) &&
                    path.matches("^/api/shifts/\\d+/close$")
            ) {
                OfflineStore.removeCache(
                        context,
                        "/api/shifts/current"
                );
            }

            if (
                    path.matches("^/api/orders/\\d+/items(?:/\\d+)?$")
            ) {
                OfflineStore.applyOptimisticOrderMutation(
                        context,
                        method,
                        path,
                        body
                );
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isQueueable(
            String method,
            String path
    ) {
        if (
                "POST".equals(method) &&
                path.matches("^/api/orders/\\d+/items$")
        ) return true;

        if (
                ("PATCH".equals(method) || "DELETE".equals(method)) &&
                path.matches("^/api/orders/\\d+/items/\\d+$")
        ) return true;

        if (
                "POST".equals(method) &&
                "/api/expenses".equals(path)
        ) return true;

        if (
                "POST".equals(method) &&
                path.matches("^/api/customers/\\d+/payment$")
        ) return true;

        if (
                "PATCH".equals(method) &&
                path.matches("^/api/customers/\\d+$")
        ) return true;

        if (
                "PATCH".equals(method) &&
                path.matches("^/api/inventory/\\d+$")
        ) return true;

        if (
                "PATCH".equals(method) &&
                path.matches("^/api/catalog/(hookah|drink|food|service)/\\d+$")
        ) return true;

        return "PATCH".equals(method) &&
                "/api/receipt-settings".equals(path);
    }

    private static String blockedMessage(String path) {
        if (path.endsWith("/settle")) {
            return "تسویه در حالت آفلاین قفل است تا از دوباره‌ثبت‌شدن پرداخت جلوگیری شود.";
        }
        if (path.contains("/reverse-settlement")) {
            return "برگرداندن تسویه فقط با اتصال مستقیم به سرور انجام می‌شود.";
        }
        if (path.contains("/merge") || path.contains("/transfer")) {
            return "انتقال و ادغام سفارش در حالت آفلاین برای جلوگیری از تداخل میزها مجاز نیست.";
        }
        if (path.contains("/backups")) {
            return "عملیات پشتیبان و بازیابی به اتصال مستقیم سرور نیاز دارد.";
        }
        return "این عملیات در حالت آفلاین برای حفظ صحت اطلاعات قابل انجام نیست.";
    }

    private static String resolveMappedPath(
            Context context,
            String path
    ) {
        Matcher matcher = LOCAL_ORDER_PATH.matcher(path);
        if (!matcher.matches()) return path;

        long localOrderId = Long.parseLong(matcher.group(1));
        long serverOrderId = OfflineStore.resolveServerOrderId(
                context,
                localOrderId
        );
        if (serverOrderId <= 0L) return path;

        String suffix = matcher.group(2) == null
                ? ""
                : matcher.group(2);
        return "/api/orders/" + serverOrderId + suffix;
    }

    public static byte[] download(
            Context context,
            String path
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(BASE + path)
                    .openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "*/*");

            String token = context
                    .getSharedPreferences(
                            "session",
                            Context.MODE_PRIVATE
                    )
                    .getString("token", "");

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + token
                );
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            if (stream != null) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                stream.close();
            }

            byte[] bytes = buffer.toByteArray();

            if (status < 200 || status >= 300) {
                String raw = new String(
                        bytes,
                        StandardCharsets.UTF_8
                );
                String message = "خطای ارتباط با سرور";
                String code = "api_error";

                try {
                    JSONObject response = new JSONObject(raw);
                    message = response.optString(
                            "message",
                            message
                    );
                    code = response.optString(
                            "error",
                            code
                    );
                } catch (Exception ignored) {
                }

                throw new ApiException(
                        status,
                        code,
                        message
                );
            }

            return bytes;
        } catch (IOException e) {
            OfflineSyncManager.markOffline(context);
            throw new ApiException(
                    0,
                    "network_unavailable",
                    "ارتباط با سرور برقرار نشد."
            );
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static JSONObject request(
            Context context,
            String method,
            String path,
            JSONObject body
    ) throws Exception {
        if ("GET".equalsIgnoreCase(method)) {
            return get(context, path);
        }
        if ("POST".equalsIgnoreCase(method)) {
            return post(context, path, body);
        }
        if ("PATCH".equalsIgnoreCase(method)) {
            return patch(context, path, body);
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            return delete(context, path);
        }

        return requestOnline(
                context,
                method,
                path,
                body,
                null
        );
    }

    static JSONObject requestOnlineForSync(
            Context context,
            String method,
            String path,
            JSONObject body,
            String operationId
    ) throws Exception {
        return requestOnline(
                context,
                method,
                path,
                body,
                operationId
        );
    }

    private static JSONObject requestOnline(
            Context context,
            String method,
            String path,
            JSONObject body,
            String operationId
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(BASE + path)
                    .openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod(method);
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
            );

            if (
                    operationId != null &&
                    !operationId.trim().isEmpty()
            ) {
                connection.setRequestProperty(
                        "X-Offline-Operation-Id",
                        operationId
                );
            }

            String token = context
                    .getSharedPreferences(
                            "session",
                            Context.MODE_PRIVATE
                    )
                    .getString("token", "");

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + token
                );
            }

            if (body != null && !"GET".equals(method)) {
                connection.setDoOutput(true);
                byte[] bytes = body
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder text = new StringBuilder();

            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                stream,
                                StandardCharsets.UTF_8
                        )
                )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        text.append(line);
                    }
                }
            }

            JSONObject response = text.length() == 0
                    ? new JSONObject()
                    : new JSONObject(text.toString());

            if (status < 200 || status >= 300) {
                String message = response.optString(
                        "message",
                        "خطای ارتباط با سرور"
                );

                throw new ApiException(
                        status,
                        response.optString(
                                "error",
                                "api_error"
                        ),
                        message
                );
            }

            return response;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(
                    0,
                    "network_unavailable",
                    "ارتباط با سرور برقرار نشد."
            );
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isTransient(ApiException e) {
        return e.status == 0 ||
                e.status == 408 ||
                e.status == 425 ||
                e.status == 429 ||
                e.status >= 500;
    }

    public static class ApiException extends Exception {
        public final int status;
        public final String code;

        public ApiException(
                int status,
                String code,
                String message
        ) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
