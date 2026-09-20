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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
        if (!phone.isEmpty()) addLine(card, "شماره تماس", phone, ink);

        long limit = customer.optLong("credit_limit", 0L);
        addLine(
                card,
                "سقف اعتبار",
                limit > 0 ? money(limit) : "بدون سقف مشخص",
                limit > 0 && balance >= limit ? red : muted
        );

        if (limit > 0) {
            addLine(card, "اعتبار آزاد", money(customer.optLong("remaining_credit", 0L)), turquoise);
        }

        int dueDays = customer.optInt("due_days", 0);
        addLine(
                card,
                "مهلت پرداخت",
                dueDays > 0 ? number(dueDays) + " روز" : "بدون سررسید",
                muted
        );

        long overdue = customer.optLong("overdue_amount", 0L);
        if (overdue > 0) {
            addLine(
                    card,
                    "بدهی معوق",
                    money(overdue) + " • " + number(customer.optLong("days_overdue", 0L)) + " روز",
                    red
            );
        }

        String notes = customer.optString("notes", "");
        if (!notes.isEmpty()) {
            TextView note = text("یادداشت: " + notes, 11, muted, false);
            note.setGravity(Gravity.RIGHT);
            note.setPadding(0, dp(8), 0, 0);
            card.addView(note);
        }

        content.addView(card);
    }

    private View entryCard(JSONObject entry) {
        LinearLayout card = card();

        long amount = entry.optLong("amount", 0L);
        String type = entry.optString("entry_type", "");
        String titleText;
        int color;

        if ("debt".equals(type)) {
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

        String note = entry.optString("note", "");
        if (!note.isEmpty()) addLine(card, "توضیح", note, ink);

        String dueAt = entry.optString("due_at", "");
        if ("debt".equals(type) && !dueAt.isEmpty()) {
            addLine(card, "سررسید", JalaliDateTime.formatUtcCompact(dueAt), brown);
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

    private void showEditDialog() {
        LinearLayout box = dialogBox();

        EditText name = field("نام مشتری", false);
        name.setText(customer.optString("name", ""));

        EditText phone = field("شماره تماس", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(customer.optString("phone", ""));

        EditText notes = field("یادداشت", false);
        notes.setText(customer.optString("notes", ""));

        box.addView(name);
        box.addView(phone);
        box.addView(notes);

        boolean canManage = PermissionStore.has(this, "manage_customer_limits");
        EditText limit = null;
        EditText due = null;
        CheckBox active = null;

        if (canManage) {
            limit = numberField(
                    "سقف اعتبار • صفر = بدون سقف",
                    String.valueOf(customer.optLong("credit_limit", 0L))
            );
            due = numberField(
                    "مهلت پرداخت (روز) • صفر = بدون سررسید",
                    String.valueOf(customer.optInt("due_days", 0))
            );
            active = new CheckBox(this);
            active.setText("مشتری فعال باشد");
            active.setTextColor(ink);
            active.setChecked(customer.optInt("active", 1) == 1);

            box.addView(limit);
            box.addView(due);
            box.addView(active);
        }

        final EditText finalLimit = limit;
        final EditText finalDue = due;
        final CheckBox finalActive = active;

        new AlertDialog.Builder(this)
                .setTitle("ویرایش مشتری")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("name", name.getText().toString().trim());
                        body.put("phone", phone.getText().toString().trim());
                        body.put("notes", notes.getText().toString().trim());

                        if (canManage) {
                            body.put("credit_limit", parseLong(finalLimit.getText().toString()));
                            body.put("due_days", parseLong(finalDue.getText().toString()));
                            body.put("active", finalActive.isChecked());
                        }

                        ApiClient.patch(this, "/api/customers/" + customerId, body);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "اطلاعات مشتری ذخیره شد.", Toast.LENGTH_SHORT).show();
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
        EditText input = new EditText(this);
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
