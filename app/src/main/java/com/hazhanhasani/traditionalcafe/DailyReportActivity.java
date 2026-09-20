package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DailyReportActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softGold = Color.rgb(249, 239, 219);

    private static final DateTimeFormatter KEY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

    private LinearLayout content;
    private ProgressBar loading;
    private TextView dateTitle;
    private LocalDate reportDate;
    private JSONObject lastReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PermissionStore.has(this, "view_reports")) {
            Toast.makeText(
                    this,
                    "مجوز مشاهده گزارش‌های مدیریتی فعال نیست.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        reportDate = JalaliDateTime.iranNow().toLocalDate();

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        syncTimeAndReload();
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

        TextView title = text("گزارش روزانه", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        dateTitle = text(jalaliDateLabel(reportDate), 12, brown, true);
        dateTitle.setGravity(Gravity.RIGHT);
        dateTitle.setPadding(0, dp(4), 0, 0);
        dateTitle.setOnClickListener(v -> showJalaliDateDialog());
        titles.addView(dateTitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(16), dp(2), dp(16), dp(10));

        Button previous = secondaryButton("روز قبل");
        previous.setOnClickListener(v -> {
            reportDate = reportDate.minusDays(1);
            reload();
        });
        nav.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button today = primaryButton("امروز");
        today.setOnClickListener(v -> {
            reportDate = JalaliDateTime.iranNow().toLocalDate();
            reload();
        });
        LinearLayout.LayoutParams todayLp =
                new LinearLayout.LayoutParams(0, dp(48), 1f);
        todayLp.setMarginStart(dp(8));
        nav.addView(today, todayLp);

        Button next = secondaryButton("روز بعد");
        next.setOnClickListener(v -> {
            reportDate = reportDate.plusDays(1);
            reload();
        });
        LinearLayout.LayoutParams nextLp =
                new LinearLayout.LayoutParams(0, dp(48), 1f);
        nextLp.setMarginStart(dp(8));
        nav.addView(next, nextLp);

        root.addView(nav);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingLp =
                new LinearLayout.LayoutParams(dp(34), dp(34));
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadingLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(28));
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

    private void syncTimeAndReload() {
        new Thread(() -> {
            try {
                JSONObject time = ApiClient.get(this, "/api/time");
                JalaliDateTime.syncServerUtc(time.optString("utc", ""));
                reportDate = JalaliDateTime.iranNow().toLocalDate();
            } catch (Exception ignored) {
            }
            runOnUiThread(this::reload);
        }).start();
    }

    private void reload() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();
        dateTitle.setText(jalaliDateLabel(reportDate));

        new Thread(() -> {
            try {
                JSONObject report = ApiClient.get(
                        this,
                        "/api/reports/daily?date=" + reportDate.format(KEY)
                );
                lastReport = report;
                runOnUiThread(() -> render(report));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONObject report) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        JSONObject summary = report.optJSONObject("summary");
        if (summary == null) summary = new JSONObject();

        renderHero(summary, report.optJSONObject("comparison"));
        renderPayments(report.optJSONArray("payment_methods"));
        renderCategories(report.optJSONArray("category_sales"));
        renderTopItems(report.optJSONArray("top_items"));
        renderStaff(report.optJSONArray("staff_sales"));
        renderExpenses(report.optJSONArray("expense_categories"), summary);
        renderCredit(report.optJSONObject("credit_flow"));
        renderHours(report.optJSONArray("hourly_sales"));
        renderShifts(report.optJSONObject("shifts"));

        Button share = primaryButton("اشتراک‌گذاری گزارش روزانه");
        share.setOnClickListener(v -> shareReport());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        lp.topMargin = dp(10);
        content.addView(share, lp);
    }

    private void renderHero(JSONObject s, JSONObject comparison) {
        LinearLayout hero = card();

        TextView title = text("خلاصه مالی روز", 17, ink, true);
        title.setGravity(Gravity.RIGHT);
        hero.addView(title);

        addMoneyRow(hero, "فروش نهایی", s.optLong("sales"), turquoise);
        addMoneyRow(hero, "جمع قبل از تخفیف", s.optLong("subtotal"), muted);
        addMoneyRow(hero, "تخفیف", s.optLong("discounts"), brown);
        addMoneyRow(hero, "بهای تمام‌شده", s.optLong("cogs"), brown);
        addMoneyRow(hero, "هزینه‌های روز", s.optLong("expenses"), red);
        addMoneyRow(hero, "سود ناخالص", s.optLong("gross_profit"), green);
        addMoneyRow(
                hero,
                "سود خالص",
                s.optLong("net_profit"),
                s.optLong("net_profit") >= 0 ? green : red
        );

        addTextRow(
                hero,
                "سفارش تسویه‌شده",
                number(s.optLong("settled_orders")) + " سفارش",
                ink
        );
        addMoneyRow(
                hero,
                "میانگین مبلغ سفارش",
                s.optLong("average_ticket"),
                turquoise
        );
        addTextRow(
                hero,
                "تعداد آیتم فروخته‌شده",
                number(s.optLong("sold_units")) + " عدد",
                ink
        );
        addTextRow(
                hero,
                "حاشیه سود خالص",
                percent(s.optDouble("profit_margin_percent", 0.0)),
                s.optDouble("profit_margin_percent", 0.0) >= 0 ? green : red
        );

        if (comparison != null) {
            Object salesRaw = comparison.opt("sales_change_percent");
            if (salesRaw != null && salesRaw != JSONObject.NULL) {
                double value = comparison.optDouble("sales_change_percent", 0.0);
                addTextRow(
                        hero,
                        "تغییر فروش نسبت به روز قبل",
                        signedPercent(value),
                        value >= 0 ? green : red
                );
            }

            Object profitRaw = comparison.opt("net_profit_change_percent");
            if (profitRaw != null && profitRaw != JSONObject.NULL) {
                double value =
                        comparison.optDouble("net_profit_change_percent", 0.0);
                addTextRow(
                        hero,
                        "تغییر سود خالص نسبت به روز قبل",
                        signedPercent(value),
                        value >= 0 ? green : red
                );
            }
        }

        content.addView(hero);
    }

    private void renderPayments(JSONArray rows) {
        sectionTitle("روش‌های پرداخت");

        if (rows == null || rows.length() == 0) {
            emptySection("برای این روز پرداختی ثبت نشده است.");
            return;
        }

        LinearLayout card = card();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            addMoneyRow(
                    card,
                    paymentLabel(row.optString("method")),
                    row.optLong("amount"),
                    paymentColor(row.optString("method"))
            );
        }
        content.addView(card);
    }

    private void renderCategories(JSONArray rows) {
        sectionTitle("فروش بر اساس بخش منو");

        if (rows == null || rows.length() == 0) {
            emptySection("فروشی برای بخش‌های منو ثبت نشده است.");
            return;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            long sales = row.optLong("sales_before_discount", 0L);
            long cost = row.optLong("cost", 0L);

            LinearLayout card = card();
            TextView title =
                    text(menuTypeLabel(row.optString("catalog_kind")), 15, ink, true);
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            addTextRow(
                    card,
                    "تعداد",
                    number(row.optLong("qty")) + " عدد",
                    muted
            );
            addMoneyRow(
                    card,
                    "فروش قبل از تخفیف سفارش",
                    sales,
                    turquoise
            );
            addMoneyRow(card, "بهای تمام‌شده", cost, brown);
            addMoneyRow(
                    card,
                    "سود خام اقلام",
                    sales - cost,
                    sales - cost >= 0 ? green : red
            );
            content.addView(card);
        }
    }

    private void renderTopItems(JSONArray rows) {
        sectionTitle("پرفروش‌ترین آیتم‌ها");

        if (rows == null || rows.length() == 0) {
            emptySection("آیتم فروخته‌شده‌ای وجود ندارد.");
            return;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            LinearLayout card = card();

            TextView title = text(
                    number(i + 1L) + ". " + row.optString("name", "آیتم"),
                    14,
                    ink,
                    true
            );
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            TextView meta = text(
                    menuTypeLabel(row.optString("catalog_kind")) +
                            " • " + number(row.optLong("qty")) + " عدد" +
                            " • " + money(row.optLong("sales_before_discount")),
                    11,
                    muted,
                    false
            );
            meta.setGravity(Gravity.RIGHT);
            meta.setPadding(0, dp(5), 0, 0);
            card.addView(meta);

            content.addView(card);
        }
    }

    private void renderStaff(JSONArray rows) {
        sectionTitle("فروش کاربران");

        if (rows == null || rows.length() == 0) {
            emptySection("فروشی توسط کاربران ثبت نشده است.");
            return;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            LinearLayout card = card();

            TextView name = text(
                    row.optString("user_name", "کاربر") +
                            " • " + roleLabel(row.optString("role")),
                    14,
                    ink,
                    true
            );
            name.setGravity(Gravity.RIGHT);
            card.addView(name);

            addMoneyRow(card, "فروش", row.optLong("sales"), turquoise);
            addTextRow(
                    card,
                    "سفارش",
                    number(row.optLong("settled_orders")) + " سفارش",
                    muted
            );

            if (row.optLong("discounts") > 0) {
                addMoneyRow(
                        card,
                        "تخفیف",
                        row.optLong("discounts"),
                        brown
                );
            }

            content.addView(card);
        }
    }

    private void renderExpenses(JSONArray rows, JSONObject summary) {
        sectionTitle("هزینه‌های روز");

        LinearLayout total = card();
        addMoneyRow(total, "جمع هزینه", summary.optLong("expenses"), red);
        addTextRow(
                total,
                "تعداد ثبت",
                number(summary.optLong("expense_count")) + " مورد",
                muted
        );
        content.addView(total);

        if (rows == null) return;

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            LinearLayout card = card();

            TextView category =
                    text(row.optString("category", "هزینه"), 13, ink, true);
            category.setGravity(Gravity.RIGHT);
            card.addView(category);

            addMoneyRow(card, "مبلغ", row.optLong("amount"), red);
            addTextRow(
                    card,
                    "تعداد",
                    number(row.optLong("count")) + " مورد",
                    muted
            );

            content.addView(card);
        }
    }

    private void renderCredit(JSONObject credit) {
        sectionTitle("حساب دفتری");

        if (credit == null) credit = new JSONObject();

        LinearLayout card = card();
        addMoneyRow(
                card,
                "نسیه ایجادشده",
                credit.optLong("credit_created"),
                brown
        );
        addMoneyRow(
                card,
                "وصول بدهی",
                credit.optLong("debt_collections"),
                green
        );

        long adjustments = credit.optLong("adjustments");
        if (adjustments != 0) {
            addMoneyRow(
                    card,
                    "اصلاح خالص حساب‌ها",
                    Math.abs(adjustments),
                    adjustments > 0 ? red : green
            );
        }

        content.addView(card);
    }

    private void renderHours(JSONArray rows) {
        sectionTitle("فروش ساعتی");

        if (rows == null || rows.length() == 0) {
            emptySection("فروش ساعتی برای این روز وجود ندارد.");
            return;
        }

        long max = 0L;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null) max = Math.max(max, row.optLong("sales"));
        }

        LinearLayout card = card();

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            long sales = row.optLong("sales");
            String hour = row.optString("hour", "00");
            String graph = bar(
                    max <= 0
                            ? 0
                            : (int) Math.round((sales * 10.0) / max)
            );

            TextView line = text(
                    JalaliDateTime.fa(hour + ":00") +
                            "  " + graph +
                            "  " + money(sales) +
                            " • " + number(row.optLong("orders")) + " سفارش",
                    11,
                    sales == max ? turquoise : muted,
                    sales == max
            );
            line.setGravity(Gravity.RIGHT);
            line.setPadding(0, dp(5), 0, dp(5));
            card.addView(line);
        }

        content.addView(card);
    }

    private void renderShifts(JSONObject shifts) {
        sectionTitle("وضعیت صندوق");

        if (shifts == null) shifts = new JSONObject();

        LinearLayout card = card();

        addTextRow(
                card,
                "شیفت بسته‌شده",
                number(shifts.optLong("closed_shifts")) + " شیفت",
                muted
        );

        long difference = shifts.optLong("cash_difference");
        String label =
                difference == 0
                        ? "اختلاف صندوق"
                        : difference > 0
                        ? "اضافه صندوق"
                        : "کسری صندوق";

        addMoneyRow(
                card,
                label,
                Math.abs(difference),
                difference == 0
                        ? green
                        : difference > 0 ? turquoise : red
        );

        content.addView(card);
    }

    private void shareReport() {
        if (lastReport == null) {
            showError("ابتدا گزارش را بارگذاری کن.");
            return;
        }

        JSONObject s = lastReport.optJSONObject("summary");
        if (s == null) s = new JSONObject();

        JSONObject credit = lastReport.optJSONObject("credit_flow");

        StringBuilder body = new StringBuilder();
        body.append("گزارش روزانه کافه سنتی\n");
        body.append(jalaliDateLabel(reportDate)).append("\n\n");
        body.append("فروش نهایی: ")
                .append(money(s.optLong("sales")))
                .append("\n");
        body.append("تعداد سفارش: ")
                .append(number(s.optLong("settled_orders")))
                .append("\n");
        body.append("تخفیف: ")
                .append(money(s.optLong("discounts")))
                .append("\n");
        body.append("بهای تمام‌شده: ")
                .append(money(s.optLong("cogs")))
                .append("\n");
        body.append("هزینه‌ها: ")
                .append(money(s.optLong("expenses")))
                .append("\n");
        body.append("سود خالص: ")
                .append(money(s.optLong("net_profit")))
                .append("\n");
        body.append("میانگین سفارش: ")
                .append(money(s.optLong("average_ticket")))
                .append("\n");

        if (credit != null) {
            body.append("نسیه جدید: ")
                    .append(money(credit.optLong("credit_created")))
                    .append("\n");
            body.append("وصول بدهی: ")
                    .append(money(credit.optLong("debt_collections")))
                    .append("\n");
        }

        JSONArray methods = lastReport.optJSONArray("payment_methods");
        if (methods != null && methods.length() > 0) {
            body.append("\nروش‌های پرداخت:\n");
            for (int i = 0; i < methods.length(); i++) {
                JSONObject row = methods.optJSONObject(i);
                if (row == null) continue;
                body.append(paymentLabel(row.optString("method")))
                        .append(": ")
                        .append(money(row.optLong("amount")))
                        .append("\n");
            }
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(
                Intent.EXTRA_SUBJECT,
                "گزارش روزانه کافه سنتی"
        );
        intent.putExtra(Intent.EXTRA_TEXT, body.toString());

        startActivity(
                Intent.createChooser(intent, "اشتراک‌گذاری گزارش")
        );
    }

    private void showJalaliDateDialog() {
        int[] j = JalaliDateTime.gregorianToJalali(
                reportDate.getYear(),
                reportDate.getMonthValue(),
                reportDate.getDayOfMonth()
        );

        EditText input = new EditText(this);
        input.setHint("مثلاً ۱۴۰۵/۰۶/۳۰");
        input.setSingleLine(true);
        input.setText(
                JalaliDateTime.fa(
                        j[0] + "/" + two(j[1]) + "/" + two(j[2])
                )
        );
        input.setSelectAllOnFocus(true);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("انتخاب تاریخ شمسی")
                .setMessage("تاریخ را به شکل سال/ماه/روز وارد کن.")
                .setView(input)
                .setPositiveButton("نمایش گزارش", (d,w) -> {
                    try {
                        String normalized =
                                latinDigits(input.getText().toString())
                                        .replace("-", "/")
                                        .trim();

                        String[] parts = normalized.split("/");
                        if (parts.length != 3) {
                            throw new IllegalArgumentException();
                        }

                        int jy = Integer.parseInt(parts[0].trim());
                        int jm = Integer.parseInt(parts[1].trim());
                        int jd = Integer.parseInt(parts[2].trim());

                        if (jy < 1300 || jy > 1600 ||
                                jm < 1 || jm > 12 ||
                                jd < 1 || jd > 31) {
                            throw new IllegalArgumentException();
                        }

                        int[] g = JalaliDateTime.jalaliToGregorian(
                                jy, jm, jd
                        );
                        LocalDate selected =
                                LocalDate.of(g[0], g[1], g[2]);

                        int[] verify =
                                JalaliDateTime.gregorianToJalali(
                                        selected.getYear(),
                                        selected.getMonthValue(),
                                        selected.getDayOfMonth()
                                );

                        if (verify[0] != jy ||
                                verify[1] != jm ||
                                verify[2] != jd) {
                            throw new IllegalArgumentException();
                        }

                        reportDate = selected;
                        reload();
                    } catch (Exception e) {
                        showError("تاریخ شمسی واردشده معتبر نیست.");
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private String jalaliDateLabel(LocalDate date) {
        int[] j = JalaliDateTime.gregorianToJalali(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth()
        );

        return JalaliDateTime.fa(
                j[0] + "/" +
                        two(j[1]) + "/" +
                        two(j[2])
        );
    }

    private void sectionTitle(String value) {
        TextView title = text(value, 17, ink, true);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(dp(2), dp(20), dp(2), dp(10));
        content.addView(title);
    }

    private void emptySection(String value) {
        TextView empty = text(value, 12, muted, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(8), dp(14), dp(8), dp(14));
        content.addView(empty);
    }

    private void addMoneyRow(
            LinearLayout parent,
            String label,
            long value,
            int color
    ) {
        addTextRow(parent, label, money(value), color);
    }

    private void addTextRow(
            LinearLayout parent,
            String label,
            String value,
            int color
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView left = text(label, 11, muted, false);
        left.setGravity(Gravity.RIGHT);
        row.addView(
                left,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView right = text(value, 12, color, true);
        right.setGravity(Gravity.LEFT);
        row.addView(right);

        parent.addView(row);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        c.setBackground(rounded(surface, 19));
        c.setElevation(dp(1));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        lp.bottomMargin = dp(9);
        c.setLayoutParams(lp);

        return c;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(turquoise, 16));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(brown);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(softGold, 16));
        return b;
    }

    private TextView text(
            String value,
            int sp,
            int color,
            boolean bold
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(
                Typeface.create(
                        "sans-serif",
                        bold ? Typeface.BOLD : Typeface.NORMAL
                )
        );
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private String paymentLabel(String method) {
        if ("cash".equals(method)) return "نقدی";
        if ("card".equals(method)) return "کارت / کارتخوان";
        if ("transfer".equals(method)) return "کارت‌به‌کارت";
        if ("credit".equals(method)) return "نسیه";
        return method;
    }

    private int paymentColor(String method) {
        if ("cash".equals(method)) return green;
        if ("credit".equals(method)) return brown;
        if ("transfer".equals(method)) return turquoise;
        return ink;
    }

    private String menuTypeLabel(String value) {
        if ("hookah".equals(value)) return "قلیان";
        if ("drink".equals(value)) return "نوشیدنی";
        if ("food".equals(value)) return "خوراکی";
        return "خدمت";
    }

    private String roleLabel(String role) {
        if ("admin".equals(role)) return "مدیر";
        if ("cashier".equals(role)) return "صندوق‌دار";
        return "شاگرد";
    }

    private String bar(int units) {
        if (units <= 0) return "";

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < units; i++) {
            b.append("▰");
        }
        return b.toString();
    }

    private String percent(double value) {
        return JalaliDateTime.fa(
                String.format(Locale.US, "%.2f%%", value)
        );
    }

    private String signedPercent(double value) {
        return JalaliDateTime.fa(
                String.format(
                        Locale.US,
                        "%s%.2f%%",
                        value > 0 ? "+" : "",
                        value
                )
        );
    }

    private String money(long value) {
        return JalaliDateTime.fa(
                String.format(Locale.US, "%,d تومان", value)
        );
    }

    private String number(long value) {
        return JalaliDateTime.fa(
                String.format(Locale.US, "%,d", value)
        );
    }

    private String two(int value) {
        return value < 10
                ? "0" + value
                : String.valueOf(value);
    }

    private String latinDigits(String value) {
        if (value == null) return "";

        String result = value;
        String fa = "۰۱۲۳۴۵۶۷۸۹";
        String ar = "٠١٢٣٤٥٦٧٨٩";

        for (int i = 0; i < 10; i++) {
            result = result.replace(
                    fa.charAt(i),
                    (char) ('0' + i)
            );
            result = result.replace(
                    ar.charAt(i),
                    (char) ('0' + i)
            );
        }

        return result;
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(
                this,
                message == null || message.trim().isEmpty()
                        ? "خطا در دریافت گزارش"
                        : message,
                Toast.LENGTH_LONG
        ).show();
    }

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
