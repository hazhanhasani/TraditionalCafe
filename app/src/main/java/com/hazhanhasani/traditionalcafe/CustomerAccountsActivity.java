package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerAccountsActivity extends Activity {

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
    private ProgressBar loading;
    private EditText search;
    private Spinner filter;
    private JSONArray customers = new JSONArray();
    private JSONObject summary = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        TextView title = text("حساب دفتری مشتریان", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text("بدهی و پرداخت مشتریان", 11, muted, false);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(16), dp(2), dp(16), dp(10));

        search = field("جستجو نام یا شماره تماس", false);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        controls.addView(search);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        filter = new Spinner(this);
        filter.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                PermissionStore.has(this, "manage_customer_limits")
                        ? new String[]{"همه مشتریان", "بدهکاران", "بدون بدهی", "غیرفعال‌ها"}
                        : new String[]{"همه مشتریان", "بدهکاران", "بدون بدهی"}
        ));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                render();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        row.addView(filter, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button add = primaryButton("+ مشتری");
        add.setOnClickListener(v -> showAddCustomer());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(dp(110), dp(50));
        addLp.setMarginStart(dp(8));
        row.addView(add, addLp);

        controls.addView(row);
        root.addView(controls);

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
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        PermissionStore.has(this, "manage_customer_limits")
                                ? "/api/customers?all=1"
                                : "/api/customers"
                );
                customers = response.optJSONArray("customers");
                if (customers == null) customers = new JSONArray();
                summary = response.optJSONObject("summary");
                if (summary == null) summary = new JSONObject();
                runOnUiThread(this::render);
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render() {
        if (content == null) return;
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        renderSummary();

        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        int mode = filter == null ? 0 : filter.getSelectedItemPosition();

        int shown = 0;
        for (int i = 0; i < customers.length(); i++) {
            JSONObject customer = customers.optJSONObject(i);
            if (customer == null) continue;

            String haystack = (
                    customer.optString("name", "") + " " +
                    customer.optString("phone", "")
            ).toLowerCase();

            if (!q.isEmpty() && !haystack.contains(q)) continue;

            long balance = customer.optLong("balance", 0L);

            if (mode == 1 && balance <= 0) continue;
            if (mode == 2 && balance != 0) continue;
            if (mode == 3 && customer.optInt("active", 1) == 1) continue;
            if (mode != 3 && customer.optInt("active", 1) == 0) continue;

            shown++;
            content.addView(customerCard(customer));
        }

        if (shown == 0) {
            TextView empty = text("مشتری مطابق جستجو یا فیلتر پیدا نشد.", 13, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(32), dp(10), dp(32));
            content.addView(empty);
        }
    }

    private void renderSummary() {
        LinearLayout card = card();

        TextView title = text("وضعیت حساب‌های دفتری", 16, ink, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        addSummaryRow(card, "کل بدهی", money(summary.optLong("total_debt", 0L)), red);
        addSummaryRow(card, "بدهکاران", number(summary.optLong("debtors", 0L)) + " نفر", brown);

        content.addView(card);
    }

    private View customerCard(JSONObject customer) {
        LinearLayout card = card();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text(customer.optString("name", "مشتری"), 16, ink, true);
        name.setGravity(Gravity.RIGHT);
        titleRow.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        if (customer.optInt("active", 1) == 0) {
            TextView inactive = text("غیرفعال", 10, red, true);
            inactive.setPadding(dp(8), dp(5), dp(8), dp(5));
            inactive.setBackground(rounded(Color.rgb(250,235,232), 12));
            titleRow.addView(inactive);
        }

        card.addView(titleRow);

        String phone = customer.optString("phone", "");
        if (!phone.isEmpty()) {
            TextView phoneView = text(phone, 11, muted, false);
            phoneView.setGravity(Gravity.RIGHT);
            phoneView.setPadding(0, dp(4), 0, 0);
            card.addView(phoneView);
        }

        long balance = customer.optLong("balance", 0L);
        TextView balanceView = text(
                "مانده بدهی: " + money(balance),
                13,
                balance > 0 ? red : green,
                true
        );
        balanceView.setGravity(Gravity.RIGHT);
        balanceView.setPadding(0, dp(7), 0, 0);
        card.addView(balanceView);

        long id = customer.optLong("id");
        String customerName = customer.optString("name", "مشتری");
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerLedgerActivity.class);
            intent.putExtra("customer_id", id);
            intent.putExtra("customer_name", customerName);
            startActivity(intent);
        });

        return card;
    }

    private void showAddCustomer() {
        LinearLayout box = dialogBox();

        EditText name = field("نام مشتری", false);
        EditText phone = field("شماره تماس", false);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText openingDebt = numberField(
                "بدهی قبلی (تومان) • صفر = بدون بدهی",
                "0"
        );

        box.addView(name);
        box.addView(phone);
        box.addView(openingDebt);

        new AlertDialog.Builder(this)
                .setTitle("مشتری جدید")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> new Thread(() -> {
                    try {
                        long debtValue = parseLong(
                                openingDebt.getText().toString()
                        );

                        JSONObject body = new JSONObject();
                        body.put(
                                "name",
                                name.getText().toString().trim()
                        );
                        body.put(
                                "phone",
                                phone.getText().toString().trim()
                        );
                        body.put("opening_debt", debtValue);

                        JSONObject response = ApiClient.post(
                                this,
                                "/api/customers",
                                body
                        );

                        runOnUiThread(() -> {
                            long savedDebt =
                                    response.optLong("opening_debt", 0L);
                            Toast.makeText(
                                    this,
                                    savedDebt > 0
                                            ? "مشتری با بدهی قبلی " +
                                              money(savedDebt) +
                                              " ثبت شد."
                                            : "مشتری ثبت شد.",
                                    Toast.LENGTH_LONG
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

    private void addSummaryRow(LinearLayout parent, String label, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView l = text(label, 11, muted, false);
        l.setGravity(Gravity.RIGHT);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = text(value, 12, color, true);
        row.addView(v);
        parent.addView(row);
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
        LabeledEditText input = new LabeledEditText(this);
        input.setFieldLabel(hint);
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
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(turquoise, 16));
        return button;
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
