package com.hazhanhasani.traditionalcafe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int brown = Color.rgb(92, 57, 35);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int turquoiseDark = Color.rgb(8, 83, 86);
    private final int gold = Color.rgb(205, 151, 71);
    private final int softGold = Color.rgb(249, 239, 219);
    private final int softTeal = Color.rgb(229, 243, 241);
    private final int green = Color.rgb(62, 135, 95);
    private final int divider = Color.rgb(235, 229, 220);

    private static final String LATEST_UPDATE_MANIFEST =
            "https://github.com/hazhanhasani/TraditionalCafe/releases/latest/download/update.json";
    private static final String LATEST_APK_FALLBACK =
            "https://github.com/hazhanhasani/TraditionalCafe/releases/latest/download/TraditionalCafe-release.apk";
    private static final long AUTO_UPDATE_CHECK_INTERVAL_MS = 60L * 1000L;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private String promptedVersion = "";
    private long activeDownloadId = -1L;
    private boolean downloadReceiverRegistered = false;

    private TextView salesAmountView;
    private TextView hookahMetricView;
    private TextView debtMetricView;
    private TextView expenseMetricView;
    private TextView profitMetricView;
    private TextView jalaliClockView;
    private TextView cashTodayView;
    private TextView cardTodayView;
    private TextView creditTodayView;
    private TextView tablesSummaryTitleView;
    private TextView tablesSummarySubtitleView;
    private TextView tablesSummaryStatusView;
    private LinearLayout dashboardTableChips;

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (jalaliClockView != null) {
                jalaliClockView.setText(JalaliDateTime.nowFull());
            }
            updateHandler.postDelayed(this, 60000L);
        }
    };

    private final Runnable periodicUpdateCheck = new Runnable() {
        @Override
        public void run() {
            checkForUpdates(false);
            updateHandler.postDelayed(this, AUTO_UPDATE_CHECK_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id != activeDownloadId) return;

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri apkUri = manager.getUriForDownloadedFile(id);
            if (apkUri != null) {
                openInstaller(apkUri);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(surface);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildScreen());
        startPeriodicUpdateChecks();
        updateHandler.removeCallbacks(clockRunnable);
        clockRunnable.run();
        refreshDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void startPeriodicUpdateChecks() {
        checkForUpdates(false);
        updateHandler.removeCallbacks(periodicUpdateCheck);
        updateHandler.postDelayed(periodicUpdateCheck, AUTO_UPDATE_CHECK_INTERVAL_MS);
    }

    @Override
    protected void onDestroy() {
        updateHandler.removeCallbacks(periodicUpdateCheck);
        updateHandler.removeCallbacks(clockRunnable);
        if (downloadReceiverRegistered) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {
            }
            downloadReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private void checkForUpdates(boolean force) {
        long now = System.currentTimeMillis();
        long lastCheck = getSharedPreferences("update_state", MODE_PRIVATE)
                .getLong("last_check", 0L);

        if (!force && now - lastCheck < AUTO_UPDATE_CHECK_INTERVAL_MS) {
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_UPDATE_MANIFEST).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "TraditionalCafe-Android");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HTTP " + responseCode);
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                reader.close();

                JSONObject update = new JSONObject(body.toString());
                String remoteVersion = update.optString("version", "");
                String localVersion = getCurrentVersionName();
                String apkUrl = update.optString("apk_url", LATEST_APK_FALLBACK);
                if (apkUrl == null || apkUrl.trim().isEmpty()) {
                    apkUrl = LATEST_APK_FALLBACK;
                }

                getSharedPreferences("update_state", MODE_PRIVATE)
                        .edit()
                        .putLong("last_check", System.currentTimeMillis())
                        .apply();

                if (isRemoteVersionNewer(remoteVersion, localVersion)) {
                    final String finalApkUrl = apkUrl;
                    if (force || !remoteVersion.equals(promptedVersion)) {
                        promptedVersion = remoteVersion;
                        runOnUiThread(() -> showUpdateDialog(remoteVersion, finalApkUrl));
                    }
                } else if (force) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "آخرین نسخه نصب است", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception error) {
                if (force) {
                    runOnUiThread(() ->
                            Toast.makeText(
                                    this,
                                    "بررسی بروزرسانی انجام نشد؛ اتصال اینترنت را بررسی کنید.",
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String getCurrentVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "0.0.0" : info.versionName;
        } catch (Exception ignored) {
            return "0.0.0";
        }
    }

    private boolean isRemoteVersionNewer(String remote, String local) {
        if (remote == null || remote.trim().isEmpty()) return false;

        String[] remoteParts = remote.split("\\.");
        String[] localParts = local == null ? new String[0] : local.split("\\.");
        int length = Math.max(remoteParts.length, localParts.length);

        for (int i = 0; i < length; i++) {
            int remotePart = i < remoteParts.length ? numericVersionPart(remoteParts[i]) : 0;
            int localPart = i < localParts.length ? numericVersionPart(localParts[i]) : 0;
            if (remotePart > localPart) return true;
            if (remotePart < localPart) return false;
        }
        return false;
    }

    private int numericVersionPart(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9].*$", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void showUpdateDialog(String remoteVersion, String apkUrl) {
        if (isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("بروزرسانی جدید آماده است")
                .setMessage(
                        "نسخه " + remoteVersion + " منتشر شده است.\n\n" +
                        "بروزرسانی داخل خود برنامه دانلود می‌شود و سپس صفحه نصب Android باز خواهد شد."
                )
                .setPositiveButton("دانلود و نصب", (dialog, which) ->
                        downloadUpdate(apkUrl, remoteVersion)
                )
                .setNegativeButton("بعداً", null)
                .show();
    }

    private void downloadUpdate(String apkUrl, String remoteVersion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(
                    this,
                    "برای نصب بروزرسانی، اجازه نصب از این برنامه را فعال کنید و دوباره روی بروزرسانی بزنید.",
                    Toast.LENGTH_LONG
            ).show();

            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(settingsIntent);
            return;
        }

        try {
            registerDownloadReceiverIfNeeded();

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("بروزرسانی کافه سنتی " + remoteVersion);
            request.setDescription("در حال دانلود نسخه جدید");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "TraditionalCafe-" + remoteVersion + ".apk"
            );

            activeDownloadId = manager.enqueue(request);
            Toast.makeText(
                    this,
                    "دانلود بروزرسانی شروع شد.",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "شروع دانلود بروزرسانی ممکن نشد.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerDownloadReceiverIfNeeded() {
        if (downloadReceiverRegistered) return;

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
        downloadReceiverRegistered = true;
    }

    private void openInstaller(Uri apkUri) {
        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(installIntent);
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "فایل دانلود شد؛ آن را از پوشه Downloads نصب کنید.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private boolean isStaff() {
        return "staff".equals(
                getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff")
        );
    }

    private View buildScreen() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(bg);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        shell.addView(scroll, scrollLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(buildTopBar());
        content.addView(buildHeroCard());

        content.addView(sectionHeader(
                isStaff() ? "فروش و نسیه امروز من" : "نمای کلی امروز",
                "لحظه‌ای"
        ));
        content.addView(buildMetrics());

        content.addView(sectionHeader(
                "دسترسی سریع",
                isStaff() ? "ابزارهای فروش من" : "همه ابزارها"
        ));
        content.addView(buildQuickActions());

        content.addView(sectionHeader("وضعیت میزها", "مدیریت میزها"));
        content.addView(buildTablesCard());

        if (!isStaff()) {
            content.addView(sectionHeader("فعالیت اخیر", "مشاهده همه"));
            content.addView(buildEmptyActivity());
        }

        shell.addView(buildBottomNav());
        return shell;
    }

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(18));

        LinearLayout avatar = new LinearLayout(this);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(turquoise, 18));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        avatarLp.setMarginEnd(dp(12));
        avatar.setLayoutParams(avatarLp);

        TextView avatarText = label("ک", 20, Color.WHITE, true);
        avatar.addView(avatarText);
        row.addView(avatar);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(titles, titlesLp);

        TextView title = label("کافه سنتی", 22, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        String sessionName = getSharedPreferences("session", MODE_PRIVATE).getString("name", "");
        String sessionRole = getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff");
        String roleLabel = "admin".equals(sessionRole) ? "مدیر" : ("cashier".equals(sessionRole) ? "صندوق‌دار" : "شاگرد");
        String subtitleValue = sessionName == null || sessionName.isEmpty()
                ? "داشبورد مدیریت روزانه"
                : sessionName + " • " + roleLabel;
        TextView subtitle = label(subtitleValue, 12, muted, false);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);

        jalaliClockView = label(JalaliDateTime.nowFull(), 11, muted, false);
        jalaliClockView.setGravity(Gravity.RIGHT);
        jalaliClockView.setPadding(0, dp(5), 0, 0);
        titles.addView(jalaliClockView);

        TextView settings = label("⋮", 30, ink, false);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v -> showAppMenu());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(40), dp(46));
        row.addView(settings, settingsLp);

        return row;
    }

    private void showAppMenu() {
        String role = getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff");
        final boolean admin = "admin".equals(role);

        String[] options = admin
                ? new String[]{"بررسی بروزرسانی", "عیب‌یابی کامل /debug", "خروج از حساب"}
                : new String[]{"بررسی بروزرسانی", "خروج از حساب"};

        new AlertDialog.Builder(this)
                .setTitle("تنظیمات")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkForUpdates(true);
                    } else if (admin && which == 1) {
                        startActivity(new Intent(this, DebugActivity.class));
                    } else {
                        getSharedPreferences("session", MODE_PRIVATE)
                                .edit()
                                .clear()
                                .apply();
                        startActivity(new Intent(this, AuthActivity.class));
                        finish();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private View buildHeroCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(76, 47, 31), turquoiseDark}
        );
        gradient.setCornerRadius(dp(26));
        card.setBackground(gradient);
        card.setElevation(dp(5));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView today = label(
                isStaff() ? "فروش امروز من" : "فروش امروز",
                14, Color.argb(210, 255, 255, 255), false
        );
        top.addView(today, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = label("روز کاری", 11, brown, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        badge.setBackground(rounded(softGold, 16));
        top.addView(badge);

        card.addView(top);

        TextView amount = label("۰ تومان", 32, Color.WHITE, true);
        salesAmountView = amount;
        amount.setGravity(Gravity.RIGHT);
        amount.setPadding(0, dp(8), 0, dp(12));
        card.addView(amount);

        View line = new View(this);
        line.setBackgroundColor(Color.argb(45, 255, 255, 255));
        card.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, 0);

        stats.addView(heroMini("نقدی", "۰"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(heroMini("کارت", "۰"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(heroMini("نسیه", "۰"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(stats);

        return card;
    }

    private View heroMini(String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.RIGHT);

        TextView t = label(title, 11, Color.argb(175, 255, 255, 255), false);
        t.setGravity(Gravity.RIGHT);
        box.addView(t);

        TextView v = label(value, 16, Color.WHITE, true);
        if ("نقدی".equals(title)) cashTodayView = v;
        if ("کارت".equals(title)) cardTodayView = v;
        if ("نسیه".equals(title)) creditTodayView = v;
        v.setGravity(Gravity.RIGHT);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(v);
        return box;
    }

    private View buildMetrics() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        grid.setUseDefaultMargins(false);

        if (isStaff()) {
            grid.addView(metricCard(
                    "قلیان امروز من", "۰", "فقط فروش‌های ثبت‌شده توسط شما",
                    R.drawable.ic_hookah, softTeal, turquoise
            ));
            grid.addView(metricCard(
                    "نسیه امروز من", "۰ تومان", "نسیه فروش‌های امروز شما",
                    R.drawable.ic_book, softGold, brown
            ));
        } else {
            grid.addView(metricCard("قلیان امروز", "۰", "ثبت نشده", R.drawable.ic_hookah, softTeal, turquoise));
            grid.addView(metricCard("طلب دفتری", "۰ تومان", "بدون بدهی", R.drawable.ic_book, softGold, brown));
            grid.addView(metricCard("هزینه امروز", "۰ تومان", "هزینه‌ای ثبت نشده", R.drawable.ic_expense, Color.rgb(250, 235, 232), Color.rgb(170, 76, 62)));
            grid.addView(metricCard("سود امروز", "۰ تومان", "پس از ثبت فروش", R.drawable.ic_chart, Color.rgb(232, 243, 235), green));
        }

        return grid;
    }

    private View metricCard(String titleText, String valueText, String hintText, int iconRes, int iconBg, int iconTint) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface, 20));
        card.setElevation(dp(1));

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(gp);

        LinearLayout iconBox = iconCircle(iconRes, iconBg, iconTint, 38);
        card.addView(iconBox);

        TextView title = label(titleText, 12, muted, false);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(0, dp(10), 0, 0);
        card.addView(title);

        TextView value = label(valueText, 18, ink, true);
        if ("قلیان امروز".equals(titleText) || "قلیان امروز من".equals(titleText)) hookahMetricView = value;
        if ("طلب دفتری".equals(titleText) || "نسیه امروز من".equals(titleText)) debtMetricView = value;
        if ("هزینه امروز".equals(titleText)) expenseMetricView = value;
        if ("سود امروز".equals(titleText)) profitMetricView = value;
        value.setGravity(Gravity.RIGHT);
        value.setPadding(0, dp(4), 0, 0);
        card.addView(value);

        TextView hint = label(hintText, 10, muted, false);
        hint.setGravity(Gravity.RIGHT);
        hint.setPadding(0, dp(5), 0, 0);
        card.addView(hint);

        return card;
    }

    private View buildQuickActions() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);

        grid.addView(actionTile("میزها", "سفارش و وضعیت میز", R.drawable.ic_table, turquoise, softTeal));
        grid.addView(actionTile("ثبت قلیان", "ثبت سریع فروش خودم", R.drawable.ic_hookah, brown, softGold));
        grid.addView(actionTile("حساب دفتری", "نسیه و پرداخت مشتری", R.drawable.ic_book, Color.rgb(92, 78, 148), Color.rgb(239, 236, 249)));

        if (isStaff()) {
            grid.addView(actionTile("فروش‌های امروز من", "فقط فروش‌های ثبت‌شده توسط من", R.drawable.ic_wallet, Color.rgb(44, 117, 78), Color.rgb(232, 243, 235)));
            grid.addView(actionTile("تسویه", "تسویه سفارش‌های خودم", R.drawable.ic_wallet, Color.rgb(44, 117, 78), Color.rgb(232, 243, 235)));
        } else {
            grid.addView(actionTile("تعریف قلیان و خدمات", "نام، قیمت فروش و هزینه", R.drawable.ic_hookah, turquoise, softTeal));
            grid.addView(actionTile("ثبت هزینه", "خرید و هزینه‌های روز", R.drawable.ic_expense, Color.rgb(177, 84, 68), Color.rgb(250, 235, 232)));
            grid.addView(actionTile("تسویه", "نقد، کارت و ترکیبی", R.drawable.ic_wallet, Color.rgb(44, 117, 78), Color.rgb(232, 243, 235)));
            grid.addView(actionTile("گزارش‌ها", "سود و زیان و عملکرد", R.drawable.ic_chart, Color.rgb(174, 124, 45), Color.rgb(251, 241, 220)));
        }

        return grid;
    }

    private View actionTile(String titleText, String subtitleText, int iconRes, int iconTint, int iconBg) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(14), dp(13), dp(14));
        card.setBackground(rounded(surface, 20));
        card.setElevation(dp(1));
        card.setOnClickListener(v -> openModuleForTitle(titleText));

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(gp);

        card.addView(iconCircle(iconRes, iconBg, iconTint, 42));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        texts.setGravity(Gravity.RIGHT);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = label(titleText, 14, ink, true);
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView sub = label(subtitleText, 10, muted, false);
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        return card;
    }

    private View buildTablesCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(surface, 22));
        card.setElevation(dp(1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        header.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = label("در حال دریافت میزها…", 15, ink, true);
        tablesSummaryTitleView = t;
        t.setGravity(Gravity.RIGHT);
        right.addView(t);

        TextView s = label("وضعیت لحظه‌ای میزها", 11, muted, false);
        tablesSummarySubtitleView = s;
        s.setGravity(Gravity.RIGHT);
        s.setPadding(0, dp(3), 0, 0);
        right.addView(s);

        TextView status = label("در حال بررسی", 11, green, true);
        tablesSummaryStatusView = status;
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        status.setBackground(rounded(Color.rgb(232, 243, 235), 16));
        header.addView(status);
        card.addView(header);

        LinearLayout chips = new LinearLayout(this);
        dashboardTableChips = chips;
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(0, dp(16), 0, 0);
        card.addView(chips);

        TextView open = label("+ باز کردن میز جدید", 13, turquoise, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable openBg = rounded(softTeal, 16);
        openBg.setStroke(dp(1), Color.rgb(196, 225, 222));
        open.setBackground(openBg);
        open.setOnClickListener(v -> openModule("tables"));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        openLp.topMargin = dp(14);
        card.addView(open, openLp);

        return card;
    }

    private View buildEmptyActivity() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(surface, 20));
        card.setElevation(dp(1));

        card.addView(iconCircle(R.drawable.ic_wallet, softGold, brown, 42));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = label("هنوز تراکنشی ثبت نشده", 13, ink, true);
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView sub = label("با ثبت اولین سفارش، فعالیت‌های امروز اینجا نمایش داده می‌شوند.", 10, muted, false);
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        return card;
    }

    private void openModuleForTitle(String title) {
        if ("میزها".equals(title)) {
            openModule("tables");
        } else if ("ثبت قلیان".equals(title)) {
            openModule("hookah");
        } else if ("فروش‌های امروز من".equals(title)) {
            openModule("my_sales");
        } else if ("تعریف قلیان و خدمات".equals(title)) {
            startActivity(new Intent(this, CatalogActivity.class));
        } else if ("حساب دفتری".equals(title)) {
            openModule("customers");
        } else if ("ثبت هزینه".equals(title)) {
            openModule("expenses");
        } else if ("تسویه".equals(title)) {
            openModule("settlement");
        } else if ("گزارش‌ها".equals(title)) {
            openModule("reports");
        }
    }

    private void openModule(String module) {
        Intent intent = new Intent(this, OperationsActivity.class);
        intent.putExtra("module", module);
        startActivity(intent);
    }

    private void refreshDashboard() {
        String token = getSharedPreferences("session", MODE_PRIVATE).getString("token", "");
        if (token == null || token.isEmpty()) return;

        new Thread(() -> {
            try {
                JSONObject timeData = ApiClient.get(this, "/api/time");
                JalaliDateTime.syncServerUtc(timeData.optString("utc", ""));
                JSONObject data = ApiClient.get(this, "/api/dashboard");
                JSONObject tablesData = ApiClient.get(this, "/api/tables");

                long sales = data.optLong("sales_today", 0L);
                long hookahs = data.optLong("hookahs_today", 0L);
                long debt = isStaff()
                        ? data.optLong("credit_today", 0L)
                        : data.optLong("total_customer_debt", 0L);
                long expenses = data.optLong("expenses_today", 0L);
                long profit = data.optLong("net_profit_today", 0L);

                long cash = 0L;
                long card = 0L;
                long credit = 0L;
                JSONArray methods = data.optJSONArray("payment_methods");
                if (methods != null) {
                    for (int i = 0; i < methods.length(); i++) {
                        JSONObject method = methods.optJSONObject(i);
                        if (method == null) continue;
                        String name = method.optString("method", "");
                        long amount = method.optLong("amount", 0L);
                        if ("cash".equals(name)) cash += amount;
                        else if ("card".equals(name) || "transfer".equals(name)) card += amount;
                        else if ("credit".equals(name)) credit += amount;
                    }
                }

                final long finalCash = cash;
                final long finalCard = card;
                final long finalCredit = credit;
                JSONArray tables = tablesData.optJSONArray("tables");

                runOnUiThread(() -> {
                    if (jalaliClockView != null) jalaliClockView.setText(JalaliDateTime.nowFull());
                    if (salesAmountView != null) salesAmountView.setText(formatMoney(sales));
                    if (hookahMetricView != null) hookahMetricView.setText(JalaliDateTime.fa(String.valueOf(hookahs)));
                    if (debtMetricView != null) debtMetricView.setText(formatMoney(debt));
                    if (expenseMetricView != null) expenseMetricView.setText(formatMoney(expenses));
                    if (profitMetricView != null) profitMetricView.setText(formatMoney(profit));
                    if (cashTodayView != null) cashTodayView.setText(formatCompactMoney(finalCash));
                    if (cardTodayView != null) cardTodayView.setText(formatCompactMoney(finalCard));
                    if (creditTodayView != null) creditTodayView.setText(formatCompactMoney(finalCredit));
                    renderDashboardTables(tables);
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void renderDashboardTables(JSONArray tables) {
        if (dashboardTableChips == null) return;
        dashboardTableChips.removeAllViews();

        int total = tables == null ? 0 : tables.length();
        int busy = 0;
        if (tables != null) {
            for (int i = 0; i < tables.length(); i++) {
                JSONObject table = tables.optJSONObject(i);
                if (table != null && table.optLong("order_id", 0L) > 0) busy++;
            }
        }

        if (tablesSummaryTitleView != null) {
            tablesSummaryTitleView.setText(JalaliDateTime.fa(String.valueOf(total)) + " میز فعال");
        }
        if (tablesSummarySubtitleView != null) {
            tablesSummarySubtitleView.setText(
                    busy == 0 ? "هیچ سفارش بازی وجود ندارد" :
                            JalaliDateTime.fa(String.valueOf(busy)) + " میز دارای سفارش باز"
            );
        }
        if (tablesSummaryStatusView != null) {
            tablesSummaryStatusView.setText(
                    busy == 0 ? "همه آزاد" : JalaliDateTime.fa(String.valueOf(busy)) + " مشغول"
            );
            tablesSummaryStatusView.setTextColor(busy == 0 ? green : brown);
            tablesSummaryStatusView.setBackground(rounded(
                    busy == 0 ? Color.rgb(232, 243, 235) : softGold, 16
            ));
        }

        if (tables == null || tables.length() == 0) return;

        int shown = Math.min(6, tables.length());
        for (int i = 0; i < shown; i++) {
            JSONObject table = tables.optJSONObject(i);
            if (table == null) continue;

            boolean isBusy = table.optLong("order_id", 0L) > 0;
            String name = table.optString("name", String.valueOf(i + 1));
            String shortName = name.replace("میز", "").trim();
            if (shortName.isEmpty()) shortName = String.valueOf(i + 1);

            TextView chip = label(JalaliDateTime.fa(shortName), 12, isBusy ? brown : green, true);
            chip.setGravity(Gravity.CENTER);

            GradientDrawable chipBg = rounded(
                    isBusy ? softGold : Color.rgb(232, 243, 235), 14
            );
            chipBg.setStroke(dp(1), isBusy ? gold : Color.rgb(196, 225, 222));
            chip.setBackground(chipBg);
            chip.setOnClickListener(v -> openModule("tables"));

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            cp.setMargins(dp(3), 0, dp(3), 0);
            dashboardTableChips.addView(chip, cp);
        }
    }

    private String formatCompactMoney(long amount) {
        if (amount == 0) return "۰";
        return JalaliDateTime.fa(String.format(java.util.Locale.US, "%,d", amount));
    }

    private String formatMoney(long amount) {
        return JalaliDateTime.fa(String.format(java.util.Locale.US, "%,d تومان", amount));
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackgroundColor(surface);
        nav.setElevation(dp(12));

        nav.addView(navItem("خانه", R.drawable.ic_home, true), new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navItem("میزها", R.drawable.ic_table, false), new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navItem("دفتر", R.drawable.ic_book, false), new LinearLayout.LayoutParams(0, dp(58), 1f));

        if (isStaff()) {
            nav.addView(navItem("فروش من", R.drawable.ic_wallet, false), new LinearLayout.LayoutParams(0, dp(58), 1f));
        } else {
            nav.addView(navItem("گزارش", R.drawable.ic_chart, false), new LinearLayout.LayoutParams(0, dp(58), 1f));
        }

        return nav;
    }

    private View navItem(String title, int iconRes, boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        if (active) item.setBackground(rounded(softTeal, 18));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(active ? turquoise : muted);
        item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView text = label(title, 10, active ? turquoise : muted, active);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(4), 0, 0);
        item.addView(text);

        item.setOnClickListener(v -> {
            if ("خانه".equals(title)) return;
            if ("میزها".equals(title)) openModule("tables");
            else if ("دفتر".equals(title)) openModule("customers");
            else if ("فروش من".equals(title)) openModule("my_sales");
            else if ("گزارش".equals(title)) openModule("reports");
        });
        return item;
    }

    private View sectionHeader(String title, String action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(22), dp(2), dp(8));

        TextView titleView = label(title, 17, ink, true);
        titleView.setGravity(Gravity.RIGHT);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView actionView = label(action, 11, turquoise, true);
        actionView.setGravity(Gravity.CENTER);
        row.addView(actionView);

        return row;
    }

    private LinearLayout iconCircle(int iconRes, int bgColor, int tint, int size) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setBackground(rounded(bgColor, size / 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        wrap.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        wrap.addView(icon, new LinearLayout.LayoutParams(dp(size / 2), dp(size / 2)));
        return wrap;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
