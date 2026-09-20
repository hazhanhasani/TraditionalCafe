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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OperationsActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softTeal = Color.rgb(229, 243, 241);

    private LinearLayout content;
    private ProgressBar loading;
    private TextView titleView;
    private String module;
    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        module = getIntent().getStringExtra("module");
        if (module == null) module = "tables";
        role = getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff");

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

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView back = text("‹", 36, ink, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        titleView = text(titleForModule(), 22, ink, true);
        titleView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(bar);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        loadingLp.setMargins(0, dp(10), 0, dp(10));
        root.addView(loading, loadingLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        return root;
    }

    private String titleForModule() {
        switch (module) {
            case "hookah": return "ثبت قلیان";
            case "customers": return "حساب دفتری";
            case "expenses": return "هزینه‌ها";
            case "settlement": return "تسویه سفارش";
            case "reports": return "گزارش‌ها";
            case "my_sales": return "فروش‌های امروز من";
            case "orders": return "مدیریت سفارش‌ها";
            default: return "میزها";
        }
    }

    private void reload() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();

        if ("staff".equals(role) && ("reports".equals(module) || "expenses".equals(module))) {
            loading.setVisibility(View.GONE);
            showAccessCard("این بخش برای حساب شاگرد نمایش داده نمی‌شود.");
            return;
        }

        new Thread(() -> {
            try {
                if ("tables".equals(module) || "hookah".equals(module) || "settlement".equals(module)) {
                    JSONObject r = ApiClient.get(this, "/api/tables");
                    runOnUiThread(() -> renderTables(r.optJSONArray("tables")));
                } else if ("customers".equals(module)) {
                    JSONObject r = ApiClient.get(this, "/api/customers");
                    runOnUiThread(() -> renderCustomers(r.optJSONArray("customers")));
                } else if ("expenses".equals(module)) {
                    JSONObject r = ApiClient.get(this, "/api/expenses");
                    runOnUiThread(() -> renderExpenses(r.optJSONArray("expenses")));
                } else if ("reports".equals(module)) {
                    JSONObject r = ApiClient.get(this, "/api/reports/summary");
                    runOnUiThread(() -> renderReports(r));
                } else if ("my_sales".equals(module)) {
                    JSONObject r = ApiClient.get(this, "/api/my-sales/today");
                    runOnUiThread(() -> renderMySales(r.optJSONArray("sales")));
                } else if ("orders".equals(module)) {
                    JSONObject r = ApiClient.get(this, "/api/orders?status=all&limit=100");
                    runOnUiThread(() -> renderOrders(r.optJSONArray("orders")));
                }
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderTables(JSONArray tables) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        TextView info = text(
                "hookah".equals(module)
                        ? "یک میز را انتخاب کن؛ اگر آزاد باشد ابتدا سفارش آن باز می‌شود."
                        : "settlement".equals(module)
                        ? "فقط میزهای دارای سفارش باز نمایش داده می‌شوند."
                        : "برای باز کردن یا ادامه سفارش، روی میز بزن.",
                12, muted, false
        );
        info.setGravity(Gravity.RIGHT);
        info.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(info);

        if (tables == null) tables = new JSONArray();
        int shown = 0;
        for (int i = 0; i < tables.length(); i++) {
            JSONObject table = tables.optJSONObject(i);
            if (table == null) continue;
            long orderId = table.optLong("order_id", 0L);
            boolean busy = table.optInt("busy", orderId > 0 ? 1 : 0) == 1;
            boolean mine = table.optInt("mine", orderId > 0 ? 1 : 0) == 1;
            if ("settlement".equals(module) && orderId <= 0) continue;
            shown++;

            String name = table.optString("name", "میز");
            long total = table.optLong("total", 0L);

            LinearLayout card = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView nameView = text(name, 17, ink, true);
            nameView.setGravity(Gravity.RIGHT);
            texts.addView(nameView);

            String openedAt = table.optString("opened_at", "");
            String stateText;
            if (busy && "staff".equals(role) && !mine) {
                stateText = "مشغول • سفارش همکار";
            } else if (busy) {
                stateText = "سفارش باز • " + money(total) +
                        (openedAt.isEmpty() ? "" : "\n" + JalaliDateTime.formatUtcCompact(openedAt));
            } else {
                stateText = "آزاد";
            }
            TextView state = text(
                    stateText,
                    12, busy ? brown : green, false
            );
            state.setGravity(Gravity.RIGHT);
            state.setPadding(0, dp(5), 0, 0);
            texts.addView(state);

            TextView badge = text(busy ? "باز" : "آزاد", 11, busy ? brown : green, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(10), dp(7), dp(10), dp(7));
            badge.setBackground(rounded(
                    busy ? Color.rgb(249,239,219) : Color.rgb(232,243,235), 14
            ));
            row.addView(badge);

            card.addView(row);
            card.setOnClickListener(v -> {
                if (busy && "staff".equals(role) && !mine) {
                    Toast.makeText(this, "این میز در اختیار همکار دیگری است.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (busy) {
                    openOrder(orderId, name, "hookah".equals(module), "settlement".equals(module));
                } else {
                    openTableAndContinue(table.optLong("id"), name);
                }
            });
            content.addView(card);
        }

        if (shown == 0) {
            showEmpty("میز دارای سفارش باز پیدا نشد.");
        }
    }

    private void openTableAndContinue(long tableId, String tableName) {
        if ("settlement".equals(module)) return;
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject r = ApiClient.post(this, "/api/tables/" + tableId + "/open", new JSONObject());
                long orderId = r.optLong("order_id");
                runOnUiThread(() -> openOrder(orderId, tableName, "hookah".equals(module), false));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void openOrder(long orderId, String tableName, boolean autoHookah, boolean autoSettle) {
        Intent i = new Intent(this, OrderActivity.class);
        i.putExtra("order_id", orderId);
        i.putExtra("table_name", tableName);
        i.putExtra("auto_hookah", autoHookah);
        i.putExtra("auto_settle", autoSettle);
        startActivity(i);
    }

    private void renderCustomers(JSONArray customers) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        Button add = primaryButton("+ افزودن مشتری دفتری");
        add.setOnClickListener(v -> showAddCustomerDialog());
        content.addView(add);

        if (customers == null || customers.length() == 0) {
            showEmpty("هنوز مشتری دفتری ثبت نشده است.");
            return;
        }

        for (int i = 0; i < customers.length(); i++) {
            JSONObject customer = customers.optJSONObject(i);
            if (customer == null) continue;

            LinearLayout c = card();
            String name = customer.optString("name", "مشتری");
            String phone = customer.optString("phone", "");
            long balance = customer.optLong("balance", 0);

            TextView n = text(name, 17, ink, true);
            n.setGravity(Gravity.RIGHT);
            c.addView(n);

            TextView b = text("مانده حساب: " + money(balance), 13, balance > 0 ? red : green, true);
            b.setGravity(Gravity.RIGHT);
            b.setPadding(0, dp(6), 0, 0);
            c.addView(b);

            if (!phone.isEmpty()) {
                TextView p = text(phone, 11, muted, false);
                p.setGravity(Gravity.RIGHT);
                p.setPadding(0, dp(4), 0, 0);
                c.addView(p);
            }

            long id = customer.optLong("id");
            c.setOnClickListener(v -> showLedger(id, name, balance));
            content.addView(c);
        }
    }

    private void showAddCustomerDialog() {
        LinearLayout box = dialogBox();
        EditText name = field("نام مشتری", false);
        EditText phone = field("شماره تماس", false);
        EditText notes = field("یادداشت", false);
        box.addView(name);
        box.addView(phone);
        box.addView(notes);

        new AlertDialog.Builder(this)
                .setTitle("مشتری دفتری جدید")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> {
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("name", name.getText().toString().trim());
                            body.put("phone", phone.getText().toString().trim());
                            body.put("notes", notes.getText().toString().trim());
                            ApiClient.post(this, "/api/customers", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "مشتری ثبت شد.", Toast.LENGTH_SHORT).show();
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

    private void showLedger(long customerId, String name, long currentBalance) {
        new Thread(() -> {
            try {
                JSONObject r = ApiClient.get(this, "/api/customers/" + customerId + "/ledger");
                JSONArray entries = r.optJSONArray("entries");
                runOnUiThread(() -> {
                    LinearLayout box = dialogBox();
                    TextView balance = text("مانده: " + money(currentBalance), 16,
                            currentBalance > 0 ? red : green, true);
                    balance.setGravity(Gravity.RIGHT);
                    balance.setPadding(0, 0, 0, dp(12));
                    box.addView(balance);

                    if (entries == null || entries.length() == 0) {
                        box.addView(text("گردش حسابی ثبت نشده است.", 12, muted, false));
                    } else {
                        int count = Math.min(entries.length(), 20);
                        for (int i = 0; i < count; i++) {
                            JSONObject e = entries.optJSONObject(i);
                            if (e == null) continue;
                            long amount = e.optLong("amount");
                            String type = e.optString("entry_type");
                            String label = "payment".equals(type) ? "پرداخت" :
                                    "debt".equals(type) ? "بدهی" : "اصلاح";
                            String at = e.optString("created_at", "");
                            String rowText = label + " • " + money(Math.abs(amount)) +
                                    (at.isEmpty() ? "" : "\n" + JalaliDateTime.formatUtcCompact(at));
                            TextView row = text(rowText, 12,
                                    amount > 0 ? red : green, false);
                            row.setGravity(Gravity.RIGHT);
                            row.setPadding(0, dp(6), 0, dp(6));
                            box.addView(row);
                        }
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(name)
                            .setView(box)
                            .setPositiveButton("ثبت پرداخت بدهی", (d,w) -> showDebtPayment(customerId))
                            .setNegativeButton("بستن", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void showDebtPayment(long customerId) {
        LinearLayout box = dialogBox();

        EditText amount = field("مبلغ پرداختی (تومان)", false);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);

        TextView methodLabel = text("روش دریافت", 12, ink, true);
        methodLabel.setGravity(Gravity.RIGHT);
        methodLabel.setPadding(dp(4), dp(5), dp(4), dp(5));

        Spinner method = new Spinner(this);
        String[] methods = new String[]{"نقدی", "کارت / کارتخوان", "کارت‌به‌کارت"};
        method.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                methods
        ));

        box.addView(amount);
        box.addView(methodLabel);
        box.addView(method, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        new AlertDialog.Builder(this)
                .setTitle("ثبت پرداخت بدهی")
                .setMessage("این دریافت در شیفت جاری شما ثبت می‌شود.")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> {
                    new Thread(() -> {
                        try {
                            long value = parseLong(amount.getText().toString());
                            JSONObject body = new JSONObject();
                            body.put("amount", value);

                            String paymentMethod = method.getSelectedItemPosition() == 1
                                    ? "card"
                                    : method.getSelectedItemPosition() == 2 ? "transfer" : "cash";
                            body.put("payment_method", paymentMethod);

                            ApiClient.post(this, "/api/customers/" + customerId + "/payment", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "پرداخت در شیفت ثبت شد.", Toast.LENGTH_SHORT).show();
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

    private void renderExpenses(JSONArray expenses) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        Button add = primaryButton("+ ثبت هزینه");
        add.setOnClickListener(v -> showAddExpenseDialog());
        content.addView(add);

        if (expenses == null || expenses.length() == 0) {
            showEmpty("هنوز هزینه‌ای ثبت نشده است.");
            return;
        }

        int count = Math.min(expenses.length(), 100);
        for (int i = 0; i < count; i++) {
            JSONObject expense = expenses.optJSONObject(i);
            if (expense == null) continue;
            LinearLayout c = card();

            TextView category = text(expense.optString("category", "هزینه"), 16, ink, true);
            category.setGravity(Gravity.RIGHT);
            c.addView(category);

            TextView amount = text(money(expense.optLong("amount")), 14, red, true);
            amount.setGravity(Gravity.RIGHT);
            amount.setPadding(0, dp(5), 0, 0);
            c.addView(amount);

            String method = expense.optString("payment_method", "cash");
            String methodFa = "card".equals(method)
                    ? "کارت / کارتخوان"
                    : "transfer".equals(method) ? "کارت‌به‌کارت" : "نقدی";
            TextView methodView = text("روش پرداخت: " + methodFa, 10, muted, false);
            methodView.setGravity(Gravity.RIGHT);
            methodView.setPadding(0, dp(4), 0, 0);
            c.addView(methodView);

            String desc = expense.optString("description", "");
            if (!desc.isEmpty()) {
                TextView d = text(desc, 11, muted, false);
                d.setGravity(Gravity.RIGHT);
                d.setPadding(0, dp(4), 0, 0);
                c.addView(d);
            }

            String createdAt = expense.optString("created_at", "");
            if (!createdAt.isEmpty()) {
                TextView at = text(JalaliDateTime.formatUtcCompact(createdAt), 10, muted, false);
                at.setGravity(Gravity.RIGHT);
                at.setPadding(0, dp(5), 0, 0);
                c.addView(at);
            }
            content.addView(c);
        }
    }

    private void showAddExpenseDialog() {
        LinearLayout box = dialogBox();
        EditText category = field("دسته هزینه؛ مثلاً خرید، حمل، تعمیر", false);
        EditText amount = field("مبلغ (تومان)", false);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText desc = field("توضیحات", false);

        Spinner method = new Spinner(this);
        String[] methods = new String[]{"نقدی", "کارت / کارتخوان", "کارت‌به‌کارت"};
        method.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                methods
        ));

        box.addView(category);
        box.addView(amount);
        box.addView(desc);
        box.addView(method, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        new AlertDialog.Builder(this)
                .setTitle("هزینه جدید")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> {
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("category", category.getText().toString().trim());
                            body.put("amount", parseLong(amount.getText().toString()));
                            body.put("description", desc.getText().toString().trim());
                            String paymentMethod = method.getSelectedItemPosition() == 1
                                    ? "card"
                                    : method.getSelectedItemPosition() == 2 ? "transfer" : "cash";
                            body.put("payment_method", paymentMethod);
                            ApiClient.post(this, "/api/expenses", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "هزینه ثبت شد.", Toast.LENGTH_SHORT).show();
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

    private void renderOrders(JSONArray orders) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        if ("staff".equals(role)) {
            showAccessCard("مدیریت کامل سفارش‌ها برای مدیر یا صندوق‌دار است.");
            return;
        }

        TextView info = text(
                "آخرین سفارش‌ها؛ برای ویرایش، انتقال، لغو، برگرداندن تسویه یا حذف کامل روی سفارش بزن.",
                12, muted, false
        );
        info.setGravity(Gravity.RIGHT);
        info.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(info);

        if (orders == null || orders.length() == 0) {
            showEmpty("سفارشی ثبت نشده است.");
            return;
        }

        for (int i = 0; i < orders.length(); i++) {
            JSONObject item = orders.optJSONObject(i);
            if (item == null) continue;

            long orderId = item.optLong("id");
            String tableName = item.optString("table_name", "میز");
            String status = item.optString("status", "open");
            long total = item.optLong("total", 0L);

            String statusFa = "settled".equals(status)
                    ? "تسویه‌شده"
                    : "cancelled".equals(status) ? "لغوشده" : "باز";

            LinearLayout card = card();

            TextView title = text(
                    "#" + JalaliDateTime.fa(String.valueOf(orderId)) +
                            " • " + tableName + " • " + statusFa,
                    15, ink, true
            );
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            TextView totalView = text(money(total), 13,
                    "cancelled".equals(status) ? muted : turquoise, true);
            totalView.setGravity(Gravity.RIGHT);
            totalView.setPadding(0, dp(5), 0, 0);
            card.addView(totalView);

            String openedAt = item.optString("opened_at", "");
            String userName = item.optString("opened_by_name", "");
            TextView detail = text(
                    (userName.isEmpty() ? "" : "ثبت‌کننده: " + userName + "\n") +
                            (openedAt.isEmpty() ? "" : JalaliDateTime.formatUtcCompact(openedAt)),
                    11, muted, false
            );
            detail.setGravity(Gravity.RIGHT);
            detail.setPadding(0, dp(5), 0, 0);
            card.addView(detail);

            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, OrderActivity.class);
                intent.putExtra("order_id", orderId);
                intent.putExtra("table_name", tableName);
                startActivity(intent);
            });
            content.addView(card);
        }
    }

    private void renderMySales(JSONArray sales) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        TextView info = text(
                "فقط فروش‌های امروز که با حساب شما ثبت و تسویه شده‌اند نمایش داده می‌شوند.",
                12, muted, false
        );
        info.setGravity(Gravity.RIGHT);
        info.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(info);

        if (sales == null || sales.length() == 0) {
            showEmpty("امروز هنوز فروش تسویه‌شده‌ای با حساب شما ثبت نشده است.");
            return;
        }

        long totalSales = 0L;
        long totalCredit = 0L;
        for (int i = 0; i < sales.length(); i++) {
            JSONObject sale = sales.optJSONObject(i);
            if (sale == null) continue;
            totalSales += sale.optLong("total", 0L);
            totalCredit += sale.optLong("credit_amount", 0L);
        }

        LinearLayout summary = card();
        TextView totalLabel = text("جمع فروش امروز من", 12, muted, false);
        totalLabel.setGravity(Gravity.RIGHT);
        summary.addView(totalLabel);

        TextView totalValue = text(money(totalSales), 22, turquoise, true);
        totalValue.setGravity(Gravity.RIGHT);
        totalValue.setPadding(0, dp(6), 0, 0);
        summary.addView(totalValue);

        TextView creditValue = text("نسیه امروز: " + money(totalCredit), 13, brown, true);
        creditValue.setGravity(Gravity.RIGHT);
        creditValue.setPadding(0, dp(6), 0, 0);
        summary.addView(creditValue);
        content.addView(summary);

        for (int i = 0; i < sales.length(); i++) {
            JSONObject sale = sales.optJSONObject(i);
            if (sale == null) continue;

            LinearLayout card = card();
            TextView title = text(
                    sale.optString("table_name", "فروش") + " • " + money(sale.optLong("total", 0L)),
                    15, ink, true
            );
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            long credit = sale.optLong("credit_amount", 0L);
            String closedAt = sale.optString("closed_at", "");
            String detail = (credit > 0 ? "نسیه: " + money(credit) + "\n" : "") +
                    (closedAt.isEmpty() ? "" : JalaliDateTime.formatUtcCompact(closedAt));
            TextView detailView = text(detail, 11, credit > 0 ? brown : muted, false);
            detailView.setGravity(Gravity.RIGHT);
            detailView.setPadding(0, dp(5), 0, 0);
            card.addView(detailView);
            content.addView(card);
        }
    }

    private void renderReports(JSONObject report) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        TextView now = text(JalaliDateTime.nowFull() + " • ساعت ایران", 12, muted, false);
        now.setGravity(Gravity.RIGHT);
        now.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(now);

        addReportCard("فروش کل", report.optLong("sales"), turquoise);
        addReportCard("تعداد سفارش تسویه‌شده", report.optLong("settled_orders"), ink, false);
        addReportCard("قلیان ثبت‌شده", report.optLong("hookahs"), brown, false);
        addReportCard("هزینه‌ها", report.optLong("expenses"), red);
        addReportCard("سود ناخالص", report.optLong("gross_profit"), green);
        addReportCard("سود خالص", report.optLong("net_profit"), green);
    }

    private void addReportCard(String label, long value, int color) {
        addReportCard(label, value, color, true);
    }

    private void addReportCard(String label, long value, int color, boolean money) {
        LinearLayout c = card();
        TextView l = text(label, 13, muted, false);
        l.setGravity(Gravity.RIGHT);
        c.addView(l);
        TextView v = text(money ? money(value) : String.valueOf(value), 22, color, true);
        v.setGravity(Gravity.RIGHT);
        v.setPadding(0, dp(7), 0, 0);
        c.addView(v);
        content.addView(c);
    }

    private void showAccessCard(String message) {
        content.removeAllViews();
        LinearLayout c = card();
        TextView t = text("محدودیت دسترسی", 18, brown, true);
        t.setGravity(Gravity.RIGHT);
        c.addView(t);
        TextView m = text(message, 13, muted, false);
        m.setGravity(Gravity.RIGHT);
        m.setPadding(0, dp(8), 0, 0);
        c.addView(m);
        content.addView(c);
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(this,
                message == null || message.trim().isEmpty() ? "خطای ارتباط با سرور" : message,
                Toast.LENGTH_LONG).show();
    }

    private void showEmpty(String message) {
        TextView empty = text(message, 13, muted, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(10), dp(32), dp(10), dp(32));
        content.addView(empty);
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
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(turquoise, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.bottomMargin = dp(14);
        button.setLayoutParams(lp);
        return button;
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
        return JalaliDateTime.fa(String.format(Locale.US, "%,d تومان", value));
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
