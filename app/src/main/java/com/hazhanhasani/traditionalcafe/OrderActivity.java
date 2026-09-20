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

public class OrderActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);

    private long orderId;
    private String tableName;
    private LinearLayout content;
    private ProgressBar loading;
    private JSONObject currentOrder;
    private boolean autoHookah;
    private boolean autoSettle;
    private boolean autoActionShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        orderId = getIntent().getLongExtra("order_id", 0L);
        tableName = getIntent().getStringExtra("table_name");
        autoHookah = getIntent().getBooleanExtra("auto_hookah", false);
        autoSettle = getIntent().getBooleanExtra("auto_settle", false);

        setContentView(buildScreen());
        reload();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView back = text("‹", 36, ink, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text(tableName == null ? "سفارش میز" : tableName, 22, ink, true);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(bar);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(36), dp(36));
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
        new Thread(() -> {
            try {
                JSONObject r = ApiClient.get(this, "/api/orders/" + orderId);
                runOnUiThread(() -> render(r));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONObject response) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        currentOrder = response.optJSONObject("order");
        JSONArray items = response.optJSONArray("items");
        if (currentOrder == null) {
            showError("سفارش پیدا نشد.");
            return;
        }

        long total = currentOrder.optLong("total", 0L);
        String status = currentOrder.optString("status", "open");

        LinearLayout summary = card();
        TextView s1 = text("جمع سفارش", 12, muted, false);
        s1.setGravity(Gravity.RIGHT);
        summary.addView(s1);
        TextView totalView = text(money(total), 28, turquoise, true);
        totalView.setGravity(Gravity.RIGHT);
        totalView.setPadding(0, dp(6), 0, 0);
        summary.addView(totalView);
        content.addView(summary);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button addHookah = smallButton("قلیان +");
        addHookah.setOnClickListener(v -> showAddItemDialog(true));
        buttons.addView(addHookah, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button addItem = smallButton("مورد +");
        addItem.setOnClickListener(v -> showAddItemDialog(false));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        addLp.setMargins(dp(8), 0, 0, 0);
        buttons.addView(addItem, addLp);

        content.addView(buttons);

        if ("open".equals(status)) {
            Button settle = primaryButton("تسویه سفارش");
            settle.setOnClickListener(v -> prepareSettlement());
            content.addView(settle);
        }

        TextView section = text("آیتم‌های سفارش", 16, ink, true);
        section.setGravity(Gravity.RIGHT);
        section.setPadding(0, dp(18), 0, dp(8));
        content.addView(section);

        if (items == null || items.length() == 0) {
            TextView empty = text("هنوز چیزی برای این میز ثبت نشده است.", 13, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            content.addView(empty);
        } else {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                LinearLayout c = card();
                TextView n = text(item.optString("name", "مورد"), 15, ink, true);
                n.setGravity(Gravity.RIGHT);
                c.addView(n);
                long qty = item.optLong("qty", 1);
                long unit = item.optLong("unit_price", 0);
                TextView meta = text(qty + " × " + money(unit), 12, muted, false);
                meta.setGravity(Gravity.RIGHT);
                meta.setPadding(0, dp(5), 0, 0);
                c.addView(meta);
                content.addView(c);
            }
        }

        if (!autoActionShown) {
            autoActionShown = true;
            if (autoHookah) showAddItemDialog(true);
            else if (autoSettle) prepareSettlement();
        }
    }

    private void showAddItemDialog(boolean hookah) {
        LinearLayout box = dialogBox();
        EditText name = field(hookah ? "طعم / نام قلیان" : "نام مورد", false);
        EditText qty = field("تعداد", false);
        qty.setInputType(InputType.TYPE_CLASS_NUMBER);
        qty.setText("1");
        EditText price = field("قیمت واحد (تومان)", false);
        price.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText cost = field("هزینه تمام‌شده (اختیاری)", false);
        cost.setInputType(InputType.TYPE_CLASS_NUMBER);

        box.addView(name);
        box.addView(qty);
        box.addView(price);
        box.addView(cost);

        new AlertDialog.Builder(this)
                .setTitle(hookah ? "ثبت قلیان" : "افزودن مورد")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> {
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("item_type", hookah ? "hookah" : "item");
                            body.put("name", name.getText().toString().trim());
                            body.put("qty", Math.max(1, parseLong(qty.getText().toString())));
                            body.put("unit_price", parseLong(price.getText().toString()));
                            body.put("unit_cost", parseLong(cost.getText().toString()));
                            ApiClient.post(this, "/api/orders/" + orderId + "/items", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "ثبت شد.", Toast.LENGTH_SHORT).show();
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

    private void prepareSettlement() {
        new Thread(() -> {
            try {
                JSONObject r = ApiClient.get(this, "/api/customers");
                JSONArray customers = r.optJSONArray("customers");
                runOnUiThread(() -> showSettlementDialog(customers));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void showSettlementDialog(JSONArray customers) {
        long total = currentOrder == null ? 0L : currentOrder.optLong("total", 0L);

        LinearLayout box = dialogBox();

        TextView totalView = text("مبلغ قابل تسویه: " + money(total), 15, turquoise, true);
        totalView.setGravity(Gravity.RIGHT);
        totalView.setPadding(0, 0, 0, dp(10));
        box.addView(totalView);

        EditText discount = numberField("مبلغ تخفیف", "0");
        EditText cash = numberField("مبلغ نقدی", "0");
        EditText card = numberField("مبلغ کارت / کارتخوان", "0");
        EditText transfer = numberField("مبلغ کارت‌به‌کارت", "0");
        EditText credit = numberField("مبلغ نسیه", "0");

        addLabeledNumberField(box, "تخفیف", "از مبلغ کل کم می‌شود", discount);
        addLabeledNumberField(box, "نقدی", "مبلغی که نقد دریافت شده", cash);
        addLabeledNumberField(box, "کارت / کارتخوان", "پرداخت با دستگاه کارتخوان", card);
        addLabeledNumberField(box, "کارت‌به‌کارت", "واریز مستقیم به کارت", transfer);
        addLabeledNumberField(box, "نسیه / حساب دفتری", "برای این مبلغ باید مشتری انتخاب شود", credit);

        List<Long> customerIds = new ArrayList<>();
        List<String> customerNames = new ArrayList<>();
        customerIds.add(0L);
        customerNames.add("انتخاب مشتری برای نسیه");

        if (customers != null) {
            for (int i = 0; i < customers.length(); i++) {
                JSONObject c = customers.optJSONObject(i);
                if (c == null) continue;
                customerIds.add(c.optLong("id"));
                customerNames.add(c.optString("name", "مشتری"));
            }
        }

        TextView customerLabel = text("مشتری نسیه", 13, ink, true);
        customerLabel.setGravity(Gravity.RIGHT);
        customerLabel.setPadding(dp(4), dp(8), dp(4), dp(6));
        box.addView(customerLabel);

        TextView customerHint = text(
                "فقط وقتی مبلغ نسیه بیشتر از صفر است، مشتری را انتخاب کن.",
                10, muted, false
        );
        customerHint.setGravity(Gravity.RIGHT);
        customerHint.setPadding(dp(4), 0, dp(4), dp(6));
        box.addView(customerHint);

        Spinner customerSpinner = new Spinner(this);
        customerSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, customerNames
        ));
        customerSpinner.setBackground(rounded(Color.rgb(247,243,235), 14));
        box.addView(customerSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ));

        new AlertDialog.Builder(this)
                .setTitle("تسویه چندروشی")
                .setView(box)
                .setPositiveButton("ثبت تسویه", (d,w) -> {
                    new Thread(() -> {
                        try {
                            long discountValue = parseLong(discount.getText().toString());
                            long cashValue = parseLong(cash.getText().toString());
                            long cardValue = parseLong(card.getText().toString());
                            long transferValue = parseLong(transfer.getText().toString());
                            long creditValue = parseLong(credit.getText().toString());

                            long expected = Math.max(0, total - discountValue);
                            long paid = cashValue + cardValue + transferValue + creditValue;
                            if (paid != expected) {
                                throw new IllegalArgumentException(
                                        "جمع روش‌های پرداخت باید دقیقاً " + money(expected) + " باشد."
                                );
                            }

                            JSONArray payments = new JSONArray();
                            if (cashValue > 0) payments.put(payment("cash", cashValue, null));
                            if (cardValue > 0) payments.put(payment("card", cardValue, null));
                            if (transferValue > 0) payments.put(payment("transfer", transferValue, null));
                            if (creditValue > 0) {
                                int pos = customerSpinner.getSelectedItemPosition();
                                long customerId = pos >= 0 && pos < customerIds.size()
                                        ? customerIds.get(pos) : 0L;
                                if (customerId <= 0) {
                                    throw new IllegalArgumentException("برای مبلغ نسیه، مشتری دفتری را انتخاب کن.");
                                }
                                payments.put(payment("credit", creditValue, customerId));
                            }

                            JSONObject body = new JSONObject();
                            body.put("discount", discountValue);
                            body.put("payments", payments);

                            ApiClient.post(this, "/api/orders/" + orderId + "/settle", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "سفارش تسویه شد.", Toast.LENGTH_LONG).show();
                                finish();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private JSONObject payment(String method, long amount, Long customerId) throws Exception {
        JSONObject p = new JSONObject();
        p.put("method", method);
        p.put("amount", amount);
        if (customerId != null) p.put("customer_id", customerId);
        return p;
    }

    private void addLabeledNumberField(
            LinearLayout parent,
            String labelText,
            String helperText,
            EditText input
    ) {
        TextView label = text(labelText, 13, ink, true);
        label.setGravity(Gravity.RIGHT);
        label.setPadding(dp(4), dp(7), dp(4), dp(4));
        parent.addView(label);

        TextView helper = text(helperText, 10, muted, false);
        helper.setGravity(Gravity.RIGHT);
        helper.setPadding(dp(4), 0, dp(4), dp(5));
        parent.addView(helper);

        parent.addView(input);
    }

    private EditText numberField(String hint, String value) {
        EditText e = field(hint, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        return e;
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(this,
                message == null || message.trim().isEmpty() ? "خطای ارتباط با سرور" : message,
                Toast.LENGTH_LONG).show();
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(rounded(surface, 20));
        c.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private Button primaryButton(String label) {
        Button button = smallButton(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.setMargins(0, dp(12), 0, dp(10));
        button.setLayoutParams(lp);
        return button;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(turquoise, 16));
        return b;
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);
        return box;
    }

    private EditText field(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(Color.rgb(250,248,244), 14));
        if (password) input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.bottomMargin = dp(9);
        input.setLayoutParams(lp);
        return input;
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
        return String.format(Locale.US, "%,d تومان", value);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim().replace(",", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
