package com.hazhanhasani.traditionalcafe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.util.List;

public class OfflineCenterActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softGold = Color.rgb(249, 239, 219);

    private LinearLayout content;
    private boolean receiverRegistered;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        registerSyncReceiver();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(syncReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSyncReceiver() {
        IntentFilter filter = new IntentFilter(
                OfflineSyncManager.ACTION_SYNC_STATE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                        syncReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                );
            } else {
                registerReceiver(syncReceiver, filter);
            }
            receiverRegistered = true;
        } catch (Exception ignored) {
        }
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(8));

        TextView back = text("‹", 36, ink, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        top.addView(
                titles,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView title = text("مرکز آفلاین", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(
                "کش محلی، سفارش‌های ذخیره‌شده و صف همگام‌سازی",
                11,
                muted,
                false
        );
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(30));

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        return root;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();

        boolean online = OfflineSyncManager.isOnline(this);
        boolean syncing = OfflineSyncManager.isSyncing();
        int queued = OfflineStore.pendingCount(this);
        int drafts = OfflineStore.pendingDraftCount(this);
        int failed = OfflineStore.failedCount(this);

        LinearLayout status = card();

        String statusText = syncing
                ? "در حال همگام‌سازی"
                : online ? "آنلاین" : "آفلاین";

        TextView title = text(
                statusText,
                20,
                syncing ? turquoise : (online ? green : red),
                true
        );
        title.setGravity(Gravity.RIGHT);
        status.addView(title);

        addLine(
                status,
                "عملیات در صف",
                number(queued),
                queued > 0 ? brown : muted
        );
        addLine(
                status,
                "سفارش آفلاین",
                number(drafts),
                drafts > 0 ? brown : muted
        );
        addLine(
                status,
                "نیازمند بررسی",
                number(failed),
                failed > 0 ? red : muted
        );
        addLine(
                status,
                "Cache محلی",
                number(OfflineStore.cacheCount(this)) + " پاسخ",
                muted
        );

        long cacheTime = OfflineStore.newestCacheTime(this);
        if (cacheTime > 0L) {
            addLine(
                    status,
                    "آخرین داده محلی",
                    JalaliDateTime.formatUtcCompact(
                            Instant.ofEpochMilli(cacheTime).toString()
                    ),
                    muted
            );
        }

        long lastSync = OfflineSyncManager.lastSync(this);
        if (lastSync > 0L) {
            addLine(
                    status,
                    "آخرین Sync موفق",
                    JalaliDateTime.formatUtcCompact(
                            Instant.ofEpochMilli(lastSync).toString()
                    ),
                    muted
            );
        }

        String lastError = OfflineSyncManager.lastError(this);
        if (lastError != null && !lastError.trim().isEmpty()) {
            TextView error = text(
                    "آخرین خطا: " + lastError,
                    11,
                    red,
                    false
            );
            error.setGravity(Gravity.RIGHT);
            error.setPadding(0, dp(8), 0, 0);
            status.addView(error);
        }

        content.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button sync = primaryButton(
                syncing ? "در حال Sync…" : "همگام‌سازی الآن"
        );
        sync.setEnabled(!syncing && online);
        sync.setAlpha(sync.isEnabled() ? 1f : 0.55f);
        sync.setOnClickListener(v -> {
            OfflineSyncManager.syncAsync(this);
            render();
        });
        actions.addView(
                sync,
                new LinearLayout.LayoutParams(0, dp(52), 1f)
        );

        Button retry = secondaryButton("تلاش مجدد خطاها");
        retry.setEnabled(failed > 0 && online);
        retry.setAlpha(retry.isEnabled() ? 1f : 0.55f);
        retry.setOnClickListener(v -> {
            OfflineSyncManager.retryFailed(this);
            Toast.makeText(
                    this,
                    "موارد خطادار دوباره وارد صف شدند.",
                    Toast.LENGTH_SHORT
            ).show();
            render();
        });

        LinearLayout.LayoutParams retryLp =
                new LinearLayout.LayoutParams(0, dp(52), 1f);
        retryLp.setMarginStart(dp(8));
        actions.addView(retry, retryLp);

        content.addView(actions);

        Button clearCache = secondaryButton("پاک‌سازی Cache این کاربر");
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        clearLp.topMargin = dp(8);
        clearCache.setLayoutParams(clearLp);
        clearCache.setOnClickListener(v -> {
            OfflineStore.clearCache(this);
            Toast.makeText(
                    this,
                    "Cache محلی پاک شد؛ صف عملیات حذف نشد.",
                    Toast.LENGTH_LONG
            ).show();
            render();
        });
        content.addView(clearCache);

        renderDrafts();
        renderPendingOperations();
        renderFailedOperations();

        TextView safety = text(
                "تسویه، برگرداندن تسویه، ادغام/انتقال میز و بازیابی بکاپ در حالت آفلاین صف نمی‌شوند. این محدودیت برای جلوگیری از دوباره‌ثبت‌شدن پول یا تداخل سفارش‌هاست.",
                11,
                muted,
                false
        );
        safety.setGravity(Gravity.RIGHT);
        safety.setPadding(dp(4), dp(16), dp(4), dp(4));
        content.addView(safety);
    }

    private void renderDrafts() {
        List<OfflineStore.DraftSummary> drafts =
                OfflineStore.draftSummaries(this);

        if (drafts.isEmpty()) return;

        section("سفارش‌های آفلاین");

        for (OfflineStore.DraftSummary draft : drafts) {
            LinearLayout card = card();

            TextView title = text(
                    draft.tableName,
                    14,
                    ink,
                    true
            );
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            String state = "failed".equals(draft.state)
                    ? "نیازمند بررسی"
                    : "در انتظار Sync";

            addLine(
                    card,
                    "وضعیت",
                    state,
                    "failed".equals(draft.state) ? red : brown
            );
            addLine(
                    card,
                    "ثبت",
                    JalaliDateTime.formatUtcCompact(
                            Instant.ofEpochMilli(draft.createdAt).toString()
                    ),
                    muted
            );

            if (
                    draft.lastError != null &&
                    !draft.lastError.trim().isEmpty()
            ) {
                TextView error = text(
                        draft.lastError,
                        10,
                        red,
                        false
                );
                error.setGravity(Gravity.RIGHT);
                error.setPadding(0, dp(6), 0, 0);
                card.addView(error);
            }

            content.addView(card);
        }
    }

    private void renderPendingOperations() {
        List<OfflineStore.PendingOperation> operations =
                OfflineStore.pendingOperations(this);

        if (operations.isEmpty()) return;

        section("عملیات در صف");

        for (OfflineStore.PendingOperation operation : operations) {
            LinearLayout card = card();

            TextView path = text(
                    operationLabel(operation.method, operation.path),
                    13,
                    ink,
                    true
            );
            path.setGravity(Gravity.RIGHT);
            card.addView(path);

            addLine(
                    card,
                    "تلاش",
                    number(operation.attempts),
                    muted
            );
            addLine(
                    card,
                    "زمان ثبت",
                    JalaliDateTime.formatUtcCompact(
                            Instant.ofEpochMilli(
                                    operation.createdAt
                            ).toString()
                    ),
                    muted
            );

            content.addView(card);
        }
    }

    private void renderFailedOperations() {
        List<OfflineStore.PendingOperation> operations =
                OfflineStore.failedOperations(this);

        if (operations.isEmpty()) return;

        section("عملیات نیازمند بررسی");

        for (OfflineStore.PendingOperation operation : operations) {
            LinearLayout card = card();

            TextView path = text(
                    operationLabel(operation.method, operation.path),
                    13,
                    red,
                    true
            );
            path.setGravity(Gravity.RIGHT);
            card.addView(path);

            if (
                    operation.lastError != null &&
                    !operation.lastError.trim().isEmpty()
            ) {
                TextView error = text(
                        operation.lastError,
                        10,
                        red,
                        false
                );
                error.setGravity(Gravity.RIGHT);
                error.setPadding(0, dp(6), 0, 0);
                card.addView(error);
            }

            content.addView(card);
        }
    }

    private String operationLabel(String method, String path) {
        if (path.matches("^/api/orders/\\d+/items$")) {
            return "افزودن آیتم به سفارش";
        }
        if (path.matches("^/api/orders/\\d+/items/\\d+$")) {
            return "ویرایش آیتم سفارش";
        }
        if ("/api/expenses".equals(path)) {
            return "ثبت هزینه";
        }
        if (path.matches("^/api/customers/\\d+/payment$")) {
            return "ثبت پرداخت مشتری";
        }
        if (path.startsWith("/api/customers/")) {
            return "ویرایش مشتری";
        }
        if (path.startsWith("/api/inventory/")) {
            return "ویرایش موجودی";
        }
        if (path.startsWith("/api/catalog/")) {
            return "ویرایش منو";
        }
        if ("/api/receipt-settings".equals(path)) {
            return "تنظیمات رسید";
        }
        return method + " " + path;
    }

    private void section(String titleText) {
        TextView title = text(titleText, 16, ink, true);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(dp(2), dp(20), dp(2), dp(8));
        content.addView(title);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(surface, 19));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(9);
        card.setLayoutParams(lp);

        return card;
    }

    private void addLine(
            LinearLayout parent,
            String label,
            String value,
            int color
    ) {
        TextView line = text(
                label + ": " + value,
                11,
                color,
                false
        );
        line.setGravity(Gravity.RIGHT);
        line.setPadding(0, dp(5), 0, 0);
        parent.addView(line);
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(turquoise, 16));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(brown);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(softGold, 16));
        return button;
    }

    private TextView text(
            String value,
            int sp,
            int color,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setTypeface(
                Typeface.create(
                        "sans-serif",
                        bold ? Typeface.BOLD : Typeface.NORMAL
                )
        );
        return text;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private String number(long value) {
        return JalaliDateTime.fa(String.valueOf(value));
    }

    private int dp(int value) {
        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
