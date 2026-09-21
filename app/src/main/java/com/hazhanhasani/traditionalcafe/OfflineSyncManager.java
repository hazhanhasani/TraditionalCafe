package com.hazhanhasani.traditionalcafe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OfflineSyncManager {

    public static final String ACTION_SYNC_STATE =
            "com.hazhanhasani.traditionalcafe.OFFLINE_SYNC_STATE";

    private static final String PREFS = "offline_state";
    private static final AtomicBoolean syncing = new AtomicBoolean(false);
    private static volatile boolean callbackRegistered = false;
    private static ConnectivityManager.NetworkCallback networkCallback;

    private OfflineSyncManager() {}

    public static synchronized void register(Context context) {
        if (callbackRegistered) return;

        Context app = context.getApplicationContext();
        ConnectivityManager manager =
                (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                markOnline(app);
                syncAsync(app);
            }

            @Override
            public void onLost(Network network) {
                if (!isOnline(app)) markOffline(app);
            }

            @Override
            public void onCapabilitiesChanged(
                    Network network,
                    NetworkCapabilities capabilities
            ) {
                if (
                        capabilities != null &&
                        capabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        )
                ) {
                    markOnline(app);
                    syncAsync(app);
                }
            }
        };

        try {
            manager.registerNetworkCallback(request, networkCallback);
            callbackRegistered = true;
        } catch (Exception ignored) {
        }

        if (isOnline(app)) {
            markOnline(app);
            syncAsync(app);
        } else {
            markOffline(app);
        }
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );
        if (manager == null) return false;

        try {
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities =
                    manager.getNetworkCapabilities(network);
            return capabilities != null &&
                    capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                    ) &&
                    capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    );
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isSyncing() {
        return syncing.get();
    }

    public static long lastSync(Context context) {
        return context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("last_sync", 0L);
    }

    public static String lastError(Context context) {
        return context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("last_error", "");
    }

    public static void markOffline(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("offline", true)
                .apply();
        broadcast(context);
    }

    public static void markOnline(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("offline", false)
                .apply();
        broadcast(context);
    }

    public static void syncAsync(Context context) {
        Context app = context.getApplicationContext();
        if (!isOnline(app)) {
            markOffline(app);
            return;
        }

        String token = app
                .getSharedPreferences("session", Context.MODE_PRIVATE)
                .getString("token", "");
        if (token == null || token.isEmpty()) return;

        if (!syncing.compareAndSet(false, true)) return;
        broadcast(app);

        new Thread(() -> {
            try {
                syncDraftOrders(app);
                syncPendingOperations(app);

                SharedPreferences.Editor editor = app
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putLong("last_sync", System.currentTimeMillis())
                        .putString("last_error", "")
                        .putBoolean("offline", false);
                editor.apply();
            } catch (Exception e) {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(
                                "last_error",
                                e.getMessage() == null
                                        ? "خطا در همگام‌سازی"
                                        : e.getMessage()
                        )
                        .apply();
            } finally {
                syncing.set(false);
                broadcast(app);
            }
        }, "TraditionalCafe-OfflineSync").start();
    }

    private static void syncDraftOrders(Context context) throws Exception {
        List<OfflineStore.DraftOrder> drafts =
                OfflineStore.syncableDrafts(context);

        for (OfflineStore.DraftOrder draft : drafts) {
            if (!isOnline(context)) {
                markOffline(context);
                return;
            }

            try {
                JSONObject response = ApiClient.requestOnlineForSync(
                        context,
                        "POST",
                        "/api/offline/orders/sync",
                        draft.payload,
                        draft.operationId
                );

                long orderId = response.optLong("order_id", 0L);
                if (orderId <= 0L) {
                    OfflineStore.markDraftFailed(
                            context,
                            draft.id,
                            "پاسخ همگام‌سازی سفارش معتبر نبود."
                    );
                    continue;
                }

                OfflineStore.markDraftSynced(
                        context,
                        draft.id,
                        orderId
                );
            } catch (ApiClient.ApiException e) {
                if (isTransient(e)) {
                    if ("offline_operation_processing".equals(e.code)) {
                        return;
                    }
                    throw e;
                }

                OfflineStore.markDraftFailed(
                        context,
                        draft.id,
                        e.getMessage()
                );
            }
        }
    }

    private static void syncPendingOperations(Context context)
            throws Exception {
        List<OfflineStore.PendingOperation> operations =
                OfflineStore.pendingOperations(context);

        for (OfflineStore.PendingOperation operation : operations) {
            if (!isOnline(context)) {
                markOffline(context);
                return;
            }

            try {
                ApiClient.requestOnlineForSync(
                        context,
                        operation.method,
                        operation.path,
                        operation.bodyJson(),
                        operation.id
                );

                OfflineStore.markOperationDone(
                        context,
                        operation.id
                );
            } catch (ApiClient.ApiException e) {
                if ("offline_operation_processing".equals(e.code)) {
                    OfflineStore.markOperationRetry(
                            context,
                            operation.id,
                            e.getMessage()
                    );
                    return;
                }

                if (isTransient(e)) {
                    OfflineStore.markOperationRetry(
                            context,
                            operation.id,
                            e.getMessage()
                    );
                    throw e;
                }

                OfflineStore.markOperationFailed(
                        context,
                        operation.id,
                        e.getMessage()
                );
            }
        }
    }

    private static boolean isTransient(ApiClient.ApiException e) {
        return e.status == 0 ||
                e.status == 408 ||
                e.status == 425 ||
                e.status == 429 ||
                e.status >= 500;
    }

    public static void retryFailed(Context context) {
        OfflineStore.retryFailedOperations(context);
        syncAsync(context);
        broadcast(context);
    }

    public static void broadcast(Context context) {
        try {
            Intent intent = new Intent(ACTION_SYNC_STATE);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
        }
    }
}
