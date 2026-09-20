package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

public class DebugActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softGreen = Color.rgb(232, 243, 235);
    private final int softRed = Color.rgb(250, 235, 232);

    private LinearLayout content;
    private ProgressBar loading;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        runDiagnostics();
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
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text("عیب‌یابی کامل پروژه", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView sub = text("/debug • بدون نمایش اطلاعات محرمانه", 11, muted, false);
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(3), 0, 0);
        titles.addView(sub);

        root.addView(top);

        summary = text("در حال اجرای تست‌ها…", 13, muted, true);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sumLp.setMargins(dp(16), 0, dp(16), dp(8));
        root.addView(summary, sumLp);

        Button rerun = new Button(this);
        rerun.setText("اجرای دوباره تست کامل");
        rerun.setTextColor(Color.WHITE);
        rerun.setTextSize(13);
        rerun.setAllCaps(false);
        rerun.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rerun.setBackground(rounded(turquoise, 16));
        rerun.setOnClickListener(v -> runDiagnostics());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        buttonLp.setMargins(dp(16), 0, dp(16), dp(8));
        root.addView(rerun, buttonLp);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadingLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        return root;
    }

    private void runDiagnostics() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();
        summary.setText("در حال اجرای تست‌ها…");
        summary.setTextColor(muted);
        summary.setBackground(rounded(Color.TRANSPARENT, 14));

        new Thread(() -> {
            int passed = 0;
            int failed = 0;

            TestCollector collector = new TestCollector();

            collector.add(checkLocalApp());
            collector.add(checkSession());
            collector.add(checkIranClock());
            collector.add(checkApi("/api/health", "Worker و D1"));
            collector.add(checkApi("/api/time", "زمان سرور ایران"));
            collector.add(checkApi("/api/debug", "دیاگ بک‌اند"));
            collector.add(checkApi("/api/dashboard", "داشبورد"));
            collector.add(checkApi("/api/tables", "میزها"));
            collector.add(checkApi("/api/catalog?type=hookah", "کاتالوگ قلیان"));
            collector.add(checkApi("/api/catalog?type=service", "کاتالوگ خدمات"));
            collector.add(checkApi("/api/customers", "حساب دفتری"));
            collector.add(checkApi("/api/expenses", "هزینه‌ها"));
            collector.add(checkUpdateManifest());

            for (TestResult result : collector.results) {
                if (result.ok) passed++;
                else failed++;
            }

            final int finalPassed = passed;
            final int finalFailed = failed;

            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                for (TestResult result : collector.results) {
                    content.addView(resultCard(result));
                }

                if (finalFailed == 0) {
                    summary.setText("همه تست‌ها سالم • " + JalaliDateTime.fa(String.valueOf(finalPassed)) + " مورد");
                    summary.setTextColor(green);
                    summary.setBackground(rounded(softGreen, 14));
                } else {
                    summary.setText(
                            JalaliDateTime.fa(String.valueOf(finalFailed)) +
                            " خطا از " +
                            JalaliDateTime.fa(String.valueOf(finalPassed + finalFailed)) +
                            " تست"
                    );
                    summary.setTextColor(red);
                    summary.setBackground(rounded(softRed, 14));
                }
            });
        }).start();
    }

    private TestResult checkLocalApp() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return ok(
                    "نسخه اپ",
                    "versionName=" + info.versionName +
                    " • versionCode=" + code +
                    " • Android=" + android.os.Build.VERSION.RELEASE
            );
        } catch (Exception e) {
            return fail("نسخه اپ", e.getMessage());
        }
    }

    private TestResult checkSession() {
        String token = getSharedPreferences("session", MODE_PRIVATE).getString("token", "");
        String user = getSharedPreferences("session", MODE_PRIVATE).getString("username", "");
        String role = getSharedPreferences("session", MODE_PRIVATE).getString("role", "");
        if (token == null || token.isEmpty()) {
            return fail("نشست کاربر", "توکن ورود وجود ندارد.");
        }
        return ok("نشست کاربر", user + " • " + role + " • token=ذخیره‌شده");
    }

    private TestResult checkIranClock() {
        try {
            String now = JalaliDateTime.nowFull();
            return ok("ساعت و تاریخ ایران", now + " • Asia/Tehran");
        } catch (Exception e) {
            return fail("ساعت و تاریخ ایران", e.getMessage());
        }
    }

    private TestResult checkApi(String path, String label) {
        try {
            JSONObject response = ApiClient.get(this, path);
            if (!response.optBoolean("ok", false)) {
                return fail(label, response.toString());
            }

            if ("/api/debug".equals(path)) {
                return ok(label, buildDebugDetails(response));
            }
            if ("/api/time".equals(path)) {
                JalaliDateTime.syncServerUtc(response.optString("utc", ""));
                return ok(
                        label,
                        response.optString("jalali", "") +
                        " • " + response.optString("timezone", "") +
                        " • اختلاف ساعت دستگاه/سرور: " +
                        JalaliDateTime.fa(String.valueOf(JalaliDateTime.getServerOffsetMillis())) +
                        "ms"
                );
            }

            return ok(label, "پاسخ سالم از سرور");
        } catch (Exception e) {
            return fail(label, safe(e.getMessage()));
        }
    }

    private String buildDebugDetails(JSONObject response) {
        JSONObject counts = response.optJSONObject("counts");
        StringBuilder details = new StringBuilder();

        details.append(response.optString("jalali_now", ""))
                .append("\nWorker: ")
                .append(response.optString("worker_version", "?"));

        if (counts != null) {
            details.append("\nUsers=")
                    .append(counts.optInt("users", -1))
                    .append(" • Tables=")
                    .append(counts.optInt("cafe_tables", -1))
                    .append(" • Orders=")
                    .append(counts.optInt("orders", -1))
                    .append(" • Open=")
                    .append(response.optInt("open_orders", -1));
        }
        return details.toString();
    }

    private TestResult checkUpdateManifest() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(
                    "https://github.com/hazhanhasani/TraditionalCafe/releases/latest/download/update.json"
            );
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "TraditionalCafe-Debug");

            int code = connection.getResponseCode();
            if (code >= 200 && code < 400) {
                return ok("سیستم بروزرسانی", "update.json قابل دریافت است • HTTP " + code);
            }
            return fail("سیستم بروزرسانی", "HTTP " + code);
        } catch (Exception e) {
            return fail("سیستم بروزرسانی", safe(e.getMessage()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private View resultCard(TestResult result) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(surface, 18));
        card.setElevation(dp(1));

        TextView indicator = text(result.ok ? "✓" : "×", 19, result.ok ? green : red, true);
        indicator.setGravity(Gravity.CENTER);
        indicator.setBackground(rounded(result.ok ? softGreen : softRed, 18));
        card.addView(indicator, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text(result.name, 14, ink, true);
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView detail = text(result.detail, 11, muted, false);
        detail.setGravity(Gravity.RIGHT);
        detail.setPadding(0, dp(4), 0, 0);
        texts.addView(detail);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(9);
        card.setLayoutParams(lp);
        return card;
    }

    private TestResult ok(String name, String detail) {
        return new TestResult(name, true, safe(detail));
    }

    private TestResult fail(String name, String detail) {
        return new TestResult(name, false, safe(detail));
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "بدون جزئیات" : value;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class TestResult {
        final String name;
        final boolean ok;
        final String detail;

        TestResult(String name, boolean ok, String detail) {
            this.name = name;
            this.ok = ok;
            this.detail = detail;
        }
    }

    private static class TestCollector {
        final java.util.List<TestResult> results = new java.util.ArrayList<>();

        void add(TestResult result) {
            results.add(result);
        }
    }
}
