package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.Locale;

public class ShiftActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softTeal = Color.rgb(229, 243, 241);
    private final int softGold = Color.rgb(249, 239, 219);

    private LinearLayout content;
    private ProgressBar loading;
    private String role = "staff";
    private JSONObject currentShift;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        role = getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff");

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) reload();
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

        TextView title = text("شیفت و صندوق", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView sub = text(
                "staff".equals(role)
                        ? "فقط شیفت و فروش خودت"
                        : "کنترل صندوق و شیفت کاربران",
                11, muted, false
        );
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(4), 0, 0);
        titles.addView(sub);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        return root;
    }

    private void reload() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();

        new Thread(() -> {
            try {
                JSONObject current = ApiClient.get(this, "/api/shifts/current");
                JSONObject history = ApiClient.get(
                        this,
                        "staff".equals(role) ? "/api/shifts/my?limit=30" : "/api/shifts?limit=100"
                );
                currentShift = current.optJSONObject("shift");
                JSONArray shifts = history.optJSONArray("shifts");
                runOnUiThread(() -> render(currentShift, shifts));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONObject shift, JSONArray history) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        TextView now = text(JalaliDateTime.nowFull() + " • ساعت ایران", 11, muted, false);
        now.setGravity(Gravity.RIGHT);
        now.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(now);

        if (shift == null) {
            renderClosedState();
        } else {
            renderCurrentShift(shift);
        }

        TextView historyTitle = text(
                "staff".equals(role) ? "شیفت‌های قبلی من" : "آخرین شیفت‌های کاربران",
                17, ink, true
        );
        historyTitle.setGravity(Gravity.RIGHT);
        historyTitle.setPadding(dp(2), dp(22), dp(2), dp(10));
        content.addView(historyTitle);

        renderHistory(history);
    }

    private void renderClosedState() {
        LinearLayout card = card();

        TextView title = text("شیفت بسته است", 18, brown, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        TextView desc = text(
                "برای ثبت فروش یا هزینه، ابتدا شیفت را با موجودی اولیه صندوق باز کن.",
                12, muted, false
        );
        desc.setGravity(Gravity.RIGHT);
        desc.setPadding(0, dp(7), 0, dp(12));
        card.addView(desc);

        EditText opening = numberField("موجودی اولیه صندوق (تومان)", "0");
        card.addView(opening);

        Button open = primaryButton("شروع شیفت");
        open.setOnClickListener(v -> openShift(opening));
        card.addView(open);

        content.addView(card);
    }

    private void renderCurrentShift(JSONObject shift) {
        LinearLayout card = card();

        TextView badge = text("شیفت باز", 12, green, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));
        badge.setBackground(rounded(Color.rgb(232,243,235), 14));
        card.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        String openedAt = shift.optString("opened_at", "");
        TextView opened = text(
                "شروع: " + (openedAt.isEmpty() ? "—" : JalaliDateTime.formatUtcCompact(openedAt)),
                12, muted, false
        );
        opened.setGravity(Gravity.RIGHT);
        opened.setPadding(0, dp(10), 0, dp(4));
        card.addView(opened);

        addMoneyRow(card, "موجودی اولیه", shift.optLong("opening_cash", 0L), ink);
        addMoneyRow(card, "جمع فروش شیفت", shift.optLong("sales_total", 0L), turquoise);
        addMoneyRow(card, "نقدی", shift.optLong("cash_sales", 0L), green);
        addMoneyRow(card, "کارت / کارتخوان", shift.optLong("card_sales", 0L), ink);
        addMoneyRow(card, "کارت‌به‌کارت", shift.optLong("transfer_sales", 0L), ink);
        addMoneyRow(card, "نسیه", shift.optLong("credit_sales", 0L), brown);

        if (!"staff".equals(role)) {
            addMoneyRow(card, "هزینه نقدی شیفت", shift.optLong("cash_expenses", 0L), red);
        }

        addMoneyRow(card, "موجودی مورد انتظار صندوق", shift.optLong("expected_cash_live", 0L), turquoise);

        TextView orders = text(
                "سفارش تسویه‌شده: " +
                        JalaliDateTime.fa(String.valueOf(shift.optLong("settled_orders", 0))),
                11, muted, false
        );
        orders.setGravity(Gravity.RIGHT);
        orders.setPadding(0, dp(6), 0, dp(10));
        card.addView(orders);

        Button close = dangerButton("بستن شیفت");
        close.setOnClickListener(v -> showCloseDialog(shift));
        card.addView(close);

        content.addView(card);
    }

    private void openShift(EditText opening) {
        new AlertDialog.Builder(this)
                .setTitle("شروع شیفت")
                .setMessage("موجودی اولیه صندوق ثبت و زمان شروع شیفت ذخیره شود؟")
                .setPositiveButton("شروع", (d,w) -> {
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("opening_cash", parseLong(opening.getText().toString()));
                            ApiClient.post(this, "/api/shifts/open", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "شیفت شروع شد.", Toast.LENGTH_LONG).show();
                                reload();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showCloseDialog(JSONObject shift) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);

        long expected = shift.optLong("expected_cash_live", 0L);

        TextView expectedView = text(
                "موجودی مورد انتظار: " + money(expected),
                13, turquoise, true
        );
        expectedView.setGravity(Gravity.RIGHT);
        expectedView.setPadding(0, 0, 0, dp(9));
        box.addView(expectedView);

        EditText counted = numberField("موجودی واقعی صندوق (تومان)", String.valueOf(expected));
        EditText note = field("توضیح پایان شیفت؛ اختیاری", false);
        box.addView(counted);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("بستن شیفت")
                .setMessage("قبل از بستن، پول نقد صندوق را بشمار. اگر سفارش بازی داشته باشی سیستم اجازه بستن نمی‌دهد.")
                .setView(box)
                .setPositiveButton("ثبت و بستن", (d,w) -> closeShift(
                        shift.optLong("id"),
                        counted,
                        note
                ))
                .setNegativeButton("لغو", null)
                .show();
    }

    private void closeShift(long shiftId, EditText counted, EditText note) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("counted_cash", parseLong(counted.getText().toString()));
                body.put("note", note.getText().toString().trim());

                JSONObject response = ApiClient.post(
                        this,
                        "/api/shifts/" + shiftId + "/close",
                        body
                );

                JSONObject closed = response.optJSONObject("shift");
                long difference = closed == null ? 0L : closed.optLong("cash_difference", 0L);

                runOnUiThread(() -> {
                    String result = difference == 0
                            ? "شیفت بدون کسری یا اضافه بسته شد."
                            : difference > 0
                            ? "شیفت بسته شد • اضافه صندوق: " + money(difference)
                            : "شیفت بسته شد • کسری صندوق: " + money(Math.abs(difference));
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                    reload();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderHistory(JSONArray history) {
        if (history == null || history.length() == 0) {
            TextView empty = text("هنوز سابقه شیفتی وجود ندارد.", 12, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(20), dp(10), dp(20));
            content.addView(empty);
            return;
        }

        for (int i = 0; i < history.length(); i++) {
            JSONObject shift = history.optJSONObject(i);
            if (shift == null) continue;

            LinearLayout card = card();

            String userName = shift.optString("user_name", "");
            String status = shift.optString("status", "closed");
            String titleText = "شیفت #" + JalaliDateTime.fa(String.valueOf(shift.optLong("id")));
            if (!"staff".equals(role) && !userName.isEmpty()) {
                titleText += " • " + userName;
            }

            TextView title = text(titleText, 14, ink, true);
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            String openedAt = shift.optString("opened_at", "");
            String closedAt = shift.optString("closed_at", "");
            TextView time = text(
                    "شروع: " + (openedAt.isEmpty() ? "—" : JalaliDateTime.formatUtcCompact(openedAt)) +
                            ("closed".equals(status)
                                    ? "\nپایان: " + (closedAt.isEmpty() ? "—" : JalaliDateTime.formatUtcCompact(closedAt))
                                    : "\nوضعیت: باز"),
                    11, muted, false
            );
            time.setGravity(Gravity.RIGHT);
            time.setPadding(0, dp(5), 0, 0);
            card.addView(time);

            if ("closed".equals(status)) {
                long expected = shift.optLong("expected_cash", 0L);
                long counted = shift.optLong("counted_cash", 0L);
                long diff = shift.optLong("cash_difference", 0L);

                TextView cash = text(
                        "مورد انتظار: " + money(expected) +
                                "\nواقعی: " + money(counted) +
                                "\n" + (diff == 0
                                ? "اختلاف: صفر"
                                : diff > 0
                                ? "اضافه: " + money(diff)
                                : "کسری: " + money(Math.abs(diff))),
                        11, diff == 0 ? green : (diff > 0 ? turquoise : red), true
                );
                cash.setGravity(Gravity.RIGHT);
                cash.setPadding(0, dp(7), 0, 0);
                card.addView(cash);
            }

            long shiftId = shift.optLong("id");
            card.setOnClickListener(v -> showShiftDetail(shiftId));
            content.addView(card);
        }
    }

    private void showShiftDetail(long shiftId) {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(this, "/api/shifts/" + shiftId);
                JSONObject shift = response.optJSONObject("shift");
                runOnUiThread(() -> {
                    if (shift == null) return;

                    String message =
                            "جمع فروش: " + money(shift.optLong("sales_total", 0L)) +
                            "\nنقدی: " + money(shift.optLong("cash_sales", 0L)) +
                            "\nکارت / کارتخوان: " + money(shift.optLong("card_sales", 0L)) +
                            "\nکارت‌به‌کارت: " + money(shift.optLong("transfer_sales", 0L)) +
                            "\nنسیه: " + money(shift.optLong("credit_sales", 0L)) +
                            "\nتعداد سفارش: " + JalaliDateTime.fa(String.valueOf(
                                    shift.optLong("settled_orders", 0L)
                            ));

                    if (!"staff".equals(role)) {
                        message += "\nهزینه کل: " + money(shift.optLong("expenses_total", 0L));
                    }

                    if ("closed".equals(shift.optString("status"))) {
                        long diff = shift.optLong("cash_difference", 0L);
                        message += "\n\nموجودی مورد انتظار: " +
                                money(shift.optLong("expected_cash", 0L)) +
                                "\nموجودی واقعی: " +
                                money(shift.optLong("counted_cash", 0L)) +
                                "\nاختلاف: " + money(diff);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("جزئیات شیفت #" +
                                    JalaliDateTime.fa(String.valueOf(shiftId)))
                            .setMessage(message)
                            .setPositiveButton("بستن", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void addMoneyRow(LinearLayout parent, String label, long value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView labelView = text(label, 12, muted, false);
        labelView.setGravity(Gravity.RIGHT);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView valueView = text(money(value), 13, color, true);
        valueView.setGravity(Gravity.LEFT);
        row.addView(valueView);

        parent.addView(row);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        c.setBackground(rounded(surface, 19));
        c.setElevation(dp(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private EditText numberField(String hint, String value) {
        EditText e = field(hint, true);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        return e;
    }

    private EditText field(String hint, boolean number) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(ink);
        e.setHintTextColor(muted);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(rounded(Color.rgb(250,248,244), 14));
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.bottomMargin = dp(9);
        e.setLayoutParams(lp);
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(turquoise, 16));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private Button dangerButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(red);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        GradientDrawable bgDrawable = rounded(Color.rgb(250,235,232), 16);
        bgDrawable.setStroke(dp(1), red);
        b.setBackground(bgDrawable);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        lp.setMargins(0, dp(7), 0, dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(
                "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private String money(long value) {
        return JalaliDateTime.fa(String.format(Locale.US, "%,d تومان", value));
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim().replace(",", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(
                this,
                message == null || message.trim().isEmpty()
                        ? "خطا در ارتباط با سرور"
                        : message,
                Toast.LENGTH_LONG
        ).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
