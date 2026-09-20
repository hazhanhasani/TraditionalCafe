package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Locale;

public class BackupRecoveryActivity extends Activity {

    private static final int SAVE_JSON = 701;
    private static final int SAVE_CSV = 702;

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int brown = Color.rgb(92, 57, 35);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softGold = Color.rgb(249, 239, 219);
    private final int softTeal = Color.rgb(229, 243, 241);

    private LinearLayout content;
    private ProgressBar loading;
    private TextView lastBackupView;
    private TextView summaryView;

    private String lastBackupId = "";
    private String pendingExportId = "";
    private String pendingExportFormat = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String role = getSharedPreferences("session", MODE_PRIVATE)
                .getString("role", "staff");
        if (!"admin".equals(role)) {
            Toast.makeText(
                    this,
                    "مدیریت پشتیبان و بازیابی فقط برای مدیر مجاز است.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        loadBackups();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

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

        TextView title = text("پشتیبان و بازیابی", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(
                "بکاپ روزانه D1، خروجی فایل و بازیابی امن اطلاعات",
                11,
                muted,
                false
        );
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> loadBackups());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

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

    private void loadBackups() {
        if (loading != null) loading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(this, "/api/backups?limit=50");
                runOnUiThread(() -> render(response));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONObject response) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        JSONObject last = response.optJSONObject("last_backup");
        JSONObject summary = response.optJSONObject("summary");
        if (summary == null) summary = new JSONObject();

        lastBackupId = last == null ? "" : last.optString("id", "");

        LinearLayout statusCard = card();

        TextView statusTitle = text("وضعیت پشتیبان‌گیری", 16, ink, true);
        statusTitle.setGravity(Gravity.RIGHT);
        statusCard.addView(statusTitle);

        String lastText;
        if (last == null) {
            lastText = "هنوز بکاپ آماده‌ای ثبت نشده است.";
        } else {
            String when = formatDate(last.optString("completed_at", last.optString("created_at", "")));
            lastText =
                    backupTypeLabel(last.optString("kind", "")) +
                    " • " + when +
                    " • " + number(last.optLong("row_count", 0)) + " ردیف";
        }

        lastBackupView = text(lastText, 12, last == null ? red : green, true);
        lastBackupView.setGravity(Gravity.RIGHT);
        lastBackupView.setPadding(0, dp(8), 0, dp(6));
        statusCard.addView(lastBackupView);

        summaryView = text(
                "کل بکاپ‌ها: " + number(summary.optLong("total", 0)) +
                        "   |   آماده: " + number(summary.optLong("ready", 0)) +
                        "   |   روزانه: " + number(summary.optLong("daily", 0)) +
                        "\nحجم ثبت‌شده: " + humanBytes(summary.optLong("total_bytes", 0)),
                11,
                muted,
                false
        );
        summaryView.setGravity(Gravity.RIGHT);
        statusCard.addView(summaryView);

        content.addView(statusCard);

        LinearLayout actions = card();

        TextView actionTitle = text("عملیات", 16, ink, true);
        actionTitle.setGravity(Gravity.RIGHT);
        actions.addView(actionTitle);

        Button manual = primaryButton("ساخت بکاپ دستی همین حالا");
        manual.setOnClickListener(v -> createManualBackup());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        actionLp.topMargin = dp(10);
        actions.addView(manual, actionLp);

        LinearLayout exportRow = new LinearLayout(this);
        exportRow.setGravity(Gravity.CENTER_VERTICAL);
        exportRow.setPadding(0, dp(8), 0, 0);

        Button json = secondaryButton("خروجی JSON");
        json.setOnClickListener(v -> startExport("json"));
        exportRow.addView(json, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button csv = secondaryButton("خروجی CSV");
        csv.setOnClickListener(v -> startExport("csv"));
        LinearLayout.LayoutParams csvLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        csvLp.setMarginStart(dp(8));
        exportRow.addView(csv, csvLp);

        actions.addView(exportRow);

        TextView hint = text(
                "قبل از حذف دائمی سفارش و قبل از هر بازیابی، سیستم به‌صورت خودکار Snapshot ایمنی می‌سازد.",
                10,
                muted,
                false
        );
        hint.setGravity(Gravity.RIGHT);
        hint.setPadding(0, dp(10), 0, 0);
        actions.addView(hint);

        content.addView(actions);

        TextView listTitle = text("تاریخچه بکاپ‌ها", 17, ink, true);
        listTitle.setGravity(Gravity.RIGHT);
        listTitle.setPadding(dp(2), dp(14), dp(2), dp(10));
        content.addView(listTitle);

        JSONArray backups = response.optJSONArray("backups");
        if (backups == null || backups.length() == 0) {
            TextView empty = text(
                    "هنوز بکاپی در سیستم وجود ندارد.",
                    13,
                    muted,
                    false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(30), dp(10), dp(30));
            content.addView(empty);
            return;
        }

        for (int i = 0; i < backups.length(); i++) {
            JSONObject backup = backups.optJSONObject(i);
            if (backup != null) content.addView(backupCard(backup));
        }
    }

    private View backupCard(JSONObject backup) {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        header.addView(
                texts,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        String kind = backup.optString("kind", "");
        String status = backup.optString("status", "");

        TextView title = text(
                backupTypeLabel(kind),
                14,
                ink,
                true
        );
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView meta = text(
                formatDate(backup.optString("completed_at", backup.optString("created_at", ""))) +
                        " • " + number(backup.optLong("row_count", 0)) + " ردیف" +
                        " • " + humanBytes(backup.optLong("size_bytes", 0)),
                10,
                muted,
                false
        );
        meta.setGravity(Gravity.RIGHT);
        meta.setPadding(0, dp(5), 0, 0);
        texts.addView(meta);

        TextView badge = text(
                "ready".equals(status) ? "آماده" :
                        ("failed".equals(status) ? "ناموفق" : "در حال ساخت"),
                9,
                "ready".equals(status) ? green : red,
                true
        );
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(6), dp(9), dp(6));
        badge.setBackground(
                rounded(
                        "ready".equals(status)
                                ? Color.rgb(232, 243, 235)
                                : Color.rgb(250, 235, 232),
                        14
                )
        );
        header.addView(badge);
        card.addView(header);

        String reason = backup.optString("reason", "");
        if (!reason.isEmpty()) {
            TextView reasonView = text("دلیل: " + reason, 10, brown, false);
            reasonView.setGravity(Gravity.RIGHT);
            reasonView.setPadding(0, dp(8), 0, 0);
            card.addView(reasonView);
        }

        String id = backup.optString("id", "");
        TextView idView = text("شناسه: " + id, 9, muted, false);
        idView.setGravity(Gravity.RIGHT);
        idView.setPadding(0, dp(6), 0, 0);
        card.addView(idView);

        if ("ready".equals(status) && !id.isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);

            Button restore = secondaryButton("بازیابی این نسخه");
            restore.setOnClickListener(v -> confirmRestore(id, kind));
            row.addView(restore, new LinearLayout.LayoutParams(0, dp(46), 1f));

            Button export = secondaryButton("JSON");
            export.setOnClickListener(v -> startExportFor(id, "json"));
            LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            expLp.setMarginStart(dp(8));
            row.addView(export, expLp);

            card.addView(row);
        }

        return card;
    }

    private void createManualBackup() {
        if (loading != null) loading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("reason", "manual_android");
                JSONObject response = ApiClient.post(this, "/api/backups", body);
                JSONObject backup = response.optJSONObject("backup");
                runOnUiThread(() -> {
                    Toast.makeText(
                            this,
                            backup == null
                                    ? "بکاپ ساخته شد."
                                    : "بکاپ با موفقیت ساخته شد.",
                            Toast.LENGTH_SHORT
                    ).show();
                    loadBackups();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void startExport(String format) {
        if (lastBackupId == null || lastBackupId.isEmpty()) {
            Toast.makeText(this, "ابتدا یک بکاپ بسازید.", Toast.LENGTH_LONG).show();
            return;
        }
        startExportFor(lastBackupId, format);
    }

    private void startExportFor(String backupId, String format) {
        pendingExportId = backupId;
        pendingExportFormat = format;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if ("csv".equals(format)) {
            intent.setType("text/csv");
            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    "TraditionalCafe-" + backupId + ".csv"
            );
            startActivityForResult(intent, SAVE_CSV);
        } else {
            intent.setType("application/json");
            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    "TraditionalCafe-" + backupId + ".json"
            );
            startActivityForResult(intent, SAVE_JSON);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode != SAVE_JSON && requestCode != SAVE_CSV) return;

        Uri uri = data.getData();
        String backupId = pendingExportId;
        String format = requestCode == SAVE_CSV ? "csv" : "json";

        if (backupId == null || backupId.isEmpty()) return;

        if (loading != null) loading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                byte[] bytes = ApiClient.download(
                        this,
                        "/api/backups/" + backupId + "/export?format=" + format
                );

                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("فایل قابل نوشتن نیست.");
                    out.write(bytes);
                    out.flush();
                }

                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(
                            this,
                            "فایل بکاپ ذخیره شد.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void confirmRestore(String backupId, String kind) {
        new AlertDialog.Builder(this)
                .setTitle("بازیابی اطلاعات")
                .setMessage(
                        "اطلاعات عملیاتی کافه به وضعیت این بکاپ برمی‌گردد. " +
                                "قبل از شروع، سیستم یک Snapshot ایمنی از وضعیت فعلی می‌سازد.\n\n" +
                                "نوع بکاپ: " + backupTypeLabel(kind)
                )
                .setPositiveButton("بازیابی", (dialog, which) -> restore(backupId))
                .setNegativeButton("لغو", null)
                .show();
    }

    private void restore(String backupId) {
        if (loading != null) loading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("confirm", "RESTORE");
                JSONObject response = ApiClient.post(
                        this,
                        "/api/backups/" + backupId + "/restore",
                        body
                );

                String safety = response.optString("safety_backup_id", "");
                int restoredTables = response.optInt("restored_tables", 0);

                runOnUiThread(() -> {
                    Toast.makeText(
                            this,
                            "بازیابی انجام شد؛ " +
                                    JalaliDateTime.fa(String.valueOf(restoredTables)) +
                                    " جدول بازیابی شد." +
                                    (safety.isEmpty() ? "" : "\nSnapshot ایمنی: " + safety),
                            Toast.LENGTH_LONG
                    ).show();
                    loadBackups();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private String backupTypeLabel(String kind) {
        if ("manual".equals(kind)) return "بکاپ دستی";
        if ("daily".equals(kind)) return "بکاپ روزانه";
        if ("pre_delete".equals(kind)) return "Snapshot قبل از حذف";
        if ("pre_restore".equals(kind)) return "Snapshot قبل از بازیابی";
        return "بکاپ";
    }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) return "بدون تاریخ";
        try {
            return JalaliDateTime.formatUtcCompact(value);
        } catch (Exception ignored) {
            return value;
        }
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) {
            return JalaliDateTime.fa(bytes + " B");
        }
        if (bytes < 1024L * 1024L) {
            return JalaliDateTime.fa(
                    String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            );
        }
        return JalaliDateTime.fa(
                String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        );
    }

    private String number(long value) {
        return JalaliDateTime.fa(
                String.format(Locale.US, "%,d", value)
        );
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
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
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
        button.setTextSize(11);
        button.setTextColor(brown);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(softGold, 16));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
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

    private void showError(String message) {
        if (loading != null) loading.setVisibility(View.GONE);
        Toast.makeText(
                this,
                message == null || message.trim().isEmpty()
                        ? "عملیات پشتیبان‌گیری انجام نشد."
                        : message,
                Toast.LENGTH_LONG
        ).show();
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
