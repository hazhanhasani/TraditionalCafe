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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class CustomerLedgerActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softGold = Color.rgb(249, 239, 219);

    private long customerId;
    private String customerName;
    private LinearLayout content;
    private ProgressBar loading;
    private JSONObject customer;
    private JSONArray entries;
    private JSONObject statement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        customerId = getIntent().getLongExtra("customer_id", 0L);
        customerName = getIntent().getStringExtra("customer_name");
        if (customerName == null || customerName.isEmpty()) customerName = "مشتری";

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

        TextView title = text(customerName, 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text("گردش کامل حساب دفتری", 11, muted, false);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, lp);

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
                JSONObject response = ApiClient.get(
                        this,
                        "/api/customers/" + customerId + "/ledger?full=1"
                );
                customer = response.optJSONObject("customer");
                entries = response.optJSONArray("entries");
                if (entries == null) entries = new JSONArray();
                statement = response.optJSONObject("statement");
                if (statement == null) statement = new JSONObject();

                runOnUiThread(this::render);
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render() {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        if (customer == null) {
            showError("اطلاعات مشتری پیدا نشد.");
            return;
        }

        renderHeader();

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button payment = primaryButton("ثبت پرداخت");
        payment.setOnClickListener(v -> showPaymentDialog());
        actions.addView(payment, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button edit = secondaryButton("ویرایش مشتری");
        edit.setOnClickListener(v -> showEditDialog());
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        editLp.setMarginStart(dp(8));
        actions.addView(edit, editLp);
        content.addView(actions);

        LinearLayout secondaryActions = new LinearLayout(this);
        secondaryActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams secondaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        secondaryLp.topMargin = dp(8);
        content.addView(secondaryActions, secondaryLp);

        Button share = secondaryButton("اشتراک صورت‌حساب");
        share.setOnClickListener(v -> shareStatement());
        secondaryActions.addView(share, new LinearLayout.LayoutParams(0, dp(50), 1f));

        if (PermissionStore.has(this, "adjust_customer_ledger")) {
            Button adjustment = secondaryButton("اصلاح حساب");
            adjustment.setOnClickListener(v -> showAdjustmentDialog());
            LinearLayout.LayoutParams adjustmentLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            adjustmentLp.setMarginStart(dp(8));
            secondaryActions.addView(adjustment, adjustmentLp);
        }

        TextView history = text("تاریخچه کامل گردش حساب", 17, ink, true);
        history.setGravity(Gravity.RIGHT);
        history.setPadding(dp(2), dp(20), dp(2), dp(10));
        content.addView(history);

        if (entries.length() == 0) {
            TextView empty = text("هنوز گردش حسابی ثبت نشده است.", 12, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(24), dp(10), dp(24));
            content.addView(empty);
            return;
        }

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            content.addView(entryCard(entry));
        }
    }

    private void renderHeader() {
        LinearLayout card = card();

        long balance = customer.optLong("balance", 0L);
        TextView balanceView = text(
                "مانده بدهی: " + money(balance),
                19,
                balance > 0 ? red : green,
                true
        );
        balanceView.setGravity(Gravity.RIGHT);
        card.addView(balanceView);

        String phone = customer.optString("phone", "");
        if (!phone.isEmpty()) {
            addLine(card, "شماره تماس", phone, ink);
        }

        if (statement != null && statement.optLong("transaction_count", 0L) > 0) {
            addLine(
                    card,
                    "جمع بدهی‌ها",
                    money(statement.optLong("debt_entries_total", 0L)),
                    muted
            );
            addLine(
                    card,
                    "جمع پرداخت‌ها",
                    money(statement.optLong("payments_total", 0L)),
                    green
            );
        }

        content.addView(card);
    }

    private View entryCard(JSONObject entry) {
        LinearLayout card = card();

        long amount = entry.optLong("amount", 0L);
        String type = entry.optString("entry_type", "");
        String note = entry.optString("note", "");
        boolean openingDebt =
                "adjustment".equals(type) &&
                amount > 0 &&
                (
                        note.startsWith("بدهی قبلی") ||
                        note.contains("مانده اولیه") ||
                        note.contains("دفتر قدیمی")
                );

        String titleText;
        int color;

        if (openingDebt) {
            titleText = "بدهی قبلی / مانده اولیه";
            color = red;
        } else if ("debt".equals(type)) {
            titleText = "بدهی / نسیه";
            color = red;
        } else if ("payment".equals(type)) {
            titleText = "پرداخت";
            color = green;
        } else {
            titleText = "اصلاح حساب";
            color = amount >= 0 ? red : green;
        }

        TextView title = text(titleText + " • " + money(Math.abs(amount)), 14, color, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        String method = entry.optString("payment_method", "");
        if (!method.isEmpty()) {
            addLine(card, "روش", paymentMethodLabel(method), muted);
        }

        long orderId = entry.optLong("order_id", 0L);
        if (orderId > 0) {
            String table = entry.optString("table_name", "");
            addLine(
                    card,
                    "سفارش",
                    "#" + number(orderId) + (table.isEmpty() ? "" : " • " + table),
                    muted
            );
        }

        if (!note.isEmpty()) addLine(card, "توضیح", note, ink);

        if (!entry.isNull("running_balance")) {
            addLine(
                    card,
                    "مانده پس از این گردش",
                    money(entry.optLong("running_balance", 0L)),
                    entry.optLong("running_balance", 0L) > 0 ? red : green
            );
        }

        String by = entry.optString("created_by_name", "");
        String created = entry.optString("created_at", "");
        TextView meta = text(
                (by.isEmpty() ? "" : "ثبت‌کننده: " + by) +
                        (created.isEmpty() ? "" : (by.isEmpty() ? "" : "\n") + JalaliDateTime.formatUtcCompact(created)),
                10, muted, false
        );
        meta.setGravity(Gravity.RIGHT);
        meta.setPadding(0, dp(7), 0, 0);
        card.addView(meta);

        if (orderId > 0) {
            TextView openOrder = text("مشاهده سفارش مرتبط", 10, turquoise, true);
            openOrder.setGravity(Gravity.RIGHT);
            openOrder.setPadding(0, dp(8), 0, 0);
            card.addView(openOrder);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, OrderActivity.class);
                intent.putExtra("order_id", orderId);
                startActivity(intent);
            });
        }

        return card;
    }

    private void showPaymentDialog() {
        long balance = customer == null ? 0L : customer.optLong("balance", 0L);
        if (balance <= 0) {
            showError("این مشتری بدهی قابل پرداخت ندارد.");
            return;
        }

        LinearLayout box = dialogBox();

        TextView balanceView = text("مانده فعلی: " + money(balance), 12, red, true);
        balanceView.setGravity(Gravity.RIGHT);
        balanceView.setPadding(dp(4), 0, dp(4), dp(8));
        box.addView(balanceView);

        EditText amount = numberField("مبلغ پرداختی", String.valueOf(balance));
        EditText note = field("توضیح پرداخت؛ اختیاری", false);

        Spinner method = new Spinner(this);
        method.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"نقدی", "کارت / کارتخوان", "کارت‌به‌کارت"}
        ));

        box.addView(amount);
        box.addView(note);
        box.addView(method, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        new AlertDialog.Builder(this)
                .setTitle("پرداخت بدهی")
                .setMessage("پرداخت کامل یا بخشی از بدهی در شیفت جاری ثبت می‌شود.")
                .setView(box)
                .setPositiveButton("ثبت پرداخت", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("amount", parseLong(amount.getText().toString()));
                        body.put("note", note.getText().toString().trim());
                        body.put(
                                "payment_method",
                                method.getSelectedItemPosition() == 1
                                        ? "card"
                                        : method.getSelectedItemPosition() == 2 ? "transfer" : "cash"
                        );

                        ApiClient.post(
                                this,
                                "/api/customers/" + customerId + "/payment",
                                body
                        );

                        runOnUiThread(() -> {
                            Toast.makeText(this, "پرداخت ثبت شد.", Toast.LENGTH_LONG).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showAdjustmentDialog() {
        LinearLayout box = dialogBox();

        Spinner direction = new Spinner(this);
        direction.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"افزایش بدهی", "کاهش بدهی"}
        ));

        EditText amount = numberField("مبلغ اصلاح (تومان)", "0");
        EditText note = field("علت اصلاح حساب • الزامی", false);

        box.addView(direction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));
        box.addView(amount);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("اصلاح دستی حساب")
                .setMessage("این عملیات فروش یا دریافت وجه نیست و فقط برای اصلاح مانده دفتر استفاده می‌شود.")
                .setView(box)
                .setPositiveButton("ثبت اصلاح", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put(
                                "direction",
                                direction.getSelectedItemPosition() == 0 ? "debt" : "credit"
                        );
                        body.put("amount", parseLong(amount.getText().toString()));
                        body.put("note", note.getText().toString().trim());

                        ApiClient.post(
                                this,
                                "/api/customers/" + customerId + "/adjustment",
                                body
                        );

                        runOnUiThread(() -> {
                            Toast.makeText(this, "اصلاح حساب ثبت شد.", Toast.LENGTH_LONG).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void shareStatement() {
        if (customer == null) return;

        StringBuilder body = new StringBuilder();
        body.append("صورت‌حساب مشتری: ")
                .append(customer.optString("name", "مشتری"))
                .append("\nمانده فعلی: ")
                .append(money(customer.optLong("balance", 0L)));

        String phone = customer.optString("phone", "");
        if (!phone.isEmpty()) body.append("\nشماره تماس: ").append(phone);

        long overdue = customer.optLong("overdue_amount", 0L);
        if (overdue > 0) {
            body.append("\nبدهی معوق: ").append(money(overdue));
        }

        body.append("\n\nگردش‌های اخیر:");
        int count = Math.min(entries == null ? 0 : entries.length(), 50);
        for (int i = 0; i < count; i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;

            String type = entry.optString("entry_type", "");
            String note = entry.optString("note", "");
            boolean openingDebt =
                    "adjustment".equals(type) &&
                    entry.optLong("amount", 0L) > 0 &&
                    (
                            note.startsWith("بدهی قبلی") ||
                            note.contains("مانده اولیه") ||
                            note.contains("دفتر قدیمی")
                    );
            String label = openingDebt
                    ? "بدهی قبلی"
                    : "debt".equals(type)
                    ? "نسیه"
                    : "payment".equals(type) ? "پرداخت" : "اصلاح";
            body.append("\n")
                    .append(label)
                    .append(" • ")
                    .append(money(Math.abs(entry.optLong("amount", 0L))));

            String created = entry.optString("created_at", "");
            if (!created.isEmpty()) {
                body.append(" • ").append(JalaliDateTime.formatUtcCompact(created));
            }
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "صورت‌حساب " + customer.optString("name", "مشتری"));
        intent.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(intent, "اشتراک صورت‌حساب"));
    }

    private void showEditDialog() {
        LinearLayout box = dialogBox();

        EditText name = field("نام مشتری", false);
        name.setText(customer.optString("name", ""));

        EditText phone = field("شماره تماس", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(customer.optString("phone", ""));

        box.addView(name);
        box.addView(phone);

        new AlertDialog.Builder(this)
                .setTitle("ویرایش مشتری")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put(
                                "name",
                                name.getText().toString().trim()
                        );
                        body.put(
                                "phone",
                                phone.getText().toString().trim()
                        );

                        ApiClient.patch(
                                this,
                                "/api/customers/" + customerId,
                                body
                        );

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    this,
                                    "اطلاعات مشتری ذخیره شد.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void addLine(LinearLayout parent, String label, String value, int color) {
        TextView line = text(label + ": " + value, 11, color, false);
        line.setGravity(Gravity.RIGHT);
        line.setPadding(0, dp(5), 0, 0);
        parent.addView(line);
    }

    private String paymentMethodLabel(String method) {
        if ("card".equals(method)) return "کارت / کارتخوان";
        if ("transfer".equals(method)) return "کارت‌به‌کارت";
        if ("cash".equals(method)) return "نقدی";
        return method;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        c.setBackground(rounded(surface, 19));
        c.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(9);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);
        return box;
    }

    private EditText field(String hint, boolean number) {
        EditText input = new LabeledEditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(Color.rgb(250,248,244), 14));
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.bottomMargin = dp(9);
        input.setLayoutParams(lp);
        return input;
    }

    private EditText numberField(String hint, String value) {
        EditText input = field(hint, true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
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

    private String money(long value) {
        return JalaliDateTime.fa(String.format(Locale.US, "%,d تومان", value));
    }

    private String number(long value) {
        return JalaliDateTime.fa(String.format(Locale.US, "%,d", value));
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
        Toast.makeText(this,
                message == null || message.trim().isEmpty() ? "خطا در ارتباط با سرور" : message,
                Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
