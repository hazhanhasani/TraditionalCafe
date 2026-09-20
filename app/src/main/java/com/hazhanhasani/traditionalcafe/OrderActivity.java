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
    private String role = "staff";

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
        role = getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff");

        setContentView(buildScreen());
        reload();
    }

    private boolean isPrivileged() {
        return "admin".equals(role) || "cashier".equals(role);
    }

    private boolean isAdmin() {
        return "admin".equals(role);
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

        String openedAt = currentOrder.optString("opened_at", "");
        if (!openedAt.isEmpty()) {
            TextView opened = text(
                    "شروع سفارش: " + JalaliDateTime.formatUtcCompact(openedAt),
                    11, muted, false
            );
            opened.setGravity(Gravity.RIGHT);
            opened.setPadding(0, dp(7), 0, 0);
            summary.addView(opened);
        }
        content.addView(summary);

        if ("open".equals(status)) {
            LinearLayout menuRow1 = new LinearLayout(this);
            menuRow1.setOrientation(LinearLayout.HORIZONTAL);
            menuRow1.setGravity(Gravity.CENTER);

            Button addHookah = smallButton("قلیان +");
            addHookah.setOnClickListener(v -> showCatalogPicker("hookah"));
            menuRow1.addView(addHookah, new LinearLayout.LayoutParams(0, dp(50), 1f));

            Button addDrink = smallButton("نوشیدنی +");
            addDrink.setOnClickListener(v -> showCatalogPicker("drink"));
            LinearLayout.LayoutParams drinkLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            drinkLp.setMarginStart(dp(8));
            menuRow1.addView(addDrink, drinkLp);
            content.addView(menuRow1);

            LinearLayout menuRow2 = new LinearLayout(this);
            menuRow2.setOrientation(LinearLayout.HORIZONTAL);
            menuRow2.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams menuRow2Lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            menuRow2Lp.topMargin = dp(8);
            menuRow2.setLayoutParams(menuRow2Lp);

            Button addFood = smallButton("خوراکی +");
            addFood.setOnClickListener(v -> showCatalogPicker("food"));
            menuRow2.addView(addFood, new LinearLayout.LayoutParams(0, dp(50), 1f));

            Button addService = smallButton("خدمت +");
            addService.setOnClickListener(v -> showCatalogPicker("service"));
            LinearLayout.LayoutParams serviceLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            serviceLp.setMarginStart(dp(8));
            menuRow2.addView(addService, serviceLp);
            content.addView(menuRow2);
        }

        if ("open".equals(status)) {
            Button settle = primaryButton("تسویه سفارش");
            settle.setOnClickListener(v -> prepareSettlement());
            content.addView(settle);

            Button manage = secondaryButton("مدیریت سفارش");
            manage.setOnClickListener(v -> showOrderManagement());
            content.addView(manage);
        } else if ("settled".equals(status) && isPrivileged()) {
            if (PermissionStore.has(this, "reverse_settlement")) {
                Button reverse = secondaryButton("برگرداندن تسویه اشتباه");
                reverse.setOnClickListener(v -> showReverseSettlementDialog());
                content.addView(reverse);
            }

            if (isAdmin()) {
                Button delete = dangerButton("حذف کامل سفارش");
                delete.setOnClickListener(v -> showHardDeleteDialog());
                content.addView(delete);
            }
        } else if ("cancelled".equals(status) && isAdmin()) {
            Button delete = dangerButton("حذف کامل سفارش");
            delete.setOnClickListener(v -> showHardDeleteDialog());
            content.addView(delete);
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
                String itemAt = item.optString("created_at", "");
                String kind = item.optString("catalog_kind", item.optString("item_type", "service"));
                String metaText = menuTypeLabel(kind) + " • " +
                        JalaliDateTime.fa(String.valueOf(qty)) + " × " + money(unit) +
                        (itemAt.isEmpty() ? "" : "\n" + JalaliDateTime.formatUtcCompact(itemAt));
                TextView meta = text(metaText, 12, muted, false);
                meta.setGravity(Gravity.RIGHT);
                meta.setPadding(0, dp(5), 0, 0);
                c.addView(meta);

                if ("open".equals(status)) {
                    long itemId = item.optLong("id");
                    String itemName = item.optString("name", "مورد");
                    long currentQty = item.optLong("qty", 1L);
                    c.setOnClickListener(v -> showItemActions(itemId, itemName, currentQty));
                }

                content.addView(c);
            }
        }

        if (!autoActionShown) {
            autoActionShown = true;
            if (autoHookah) showCatalogPicker("hookah");
            else if (autoSettle) prepareSettlement();
        }
    }

    private void showItemActions(long itemId, String name, long currentQty) {
        String[] options = new String[]{"تغییر تعداد", "حذف از سفارش"};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditQuantityDialog(itemId, name, currentQty);
                    else showDeleteItemDialog(itemId, name);
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showEditQuantityDialog(long itemId, String name, long currentQty) {
        EditText qty = numberField("تعداد", String.valueOf(currentQty));
        qty.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("تغییر تعداد • " + name)
                .setView(qty)
                .setPositiveButton("ذخیره", (d,w) -> {
                    new Thread(() -> {
                        try {
                            long value = parseLong(qty.getText().toString());
                            if (value <= 0) throw new IllegalArgumentException("تعداد باید بیشتر از صفر باشد.");
                            JSONObject body = new JSONObject();
                            body.put("qty", value);
                            ApiClient.patch(this,
                                    "/api/orders/" + orderId + "/items/" + itemId,
                                    body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "تعداد اصلاح شد.", Toast.LENGTH_SHORT).show();
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

    private void showDeleteItemDialog(long itemId, String name) {
        new AlertDialog.Builder(this)
                .setTitle("حذف از سفارش")
                .setMessage("«" + name + "» از این سفارش حذف شود؟")
                .setPositiveButton("حذف", (d,w) -> {
                    new Thread(() -> {
                        try {
                            ApiClient.delete(this,
                                    "/api/orders/" + orderId + "/items/" + itemId);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "آیتم حذف شد.", Toast.LENGTH_SHORT).show();
                                reload();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("خیر", null)
                .show();
    }

    private void showOrderManagement() {
        List<String> options = new ArrayList<>();
        options.add("انتقال سفارش به میز دیگر");
        options.add("ادغام با سفارش میز دیگر");
        options.add("لغو سفارش");
        if (isAdmin()) options.add("حذف کامل سفارش");

        new AlertDialog.Builder(this)
                .setTitle("مدیریت سفارش")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String selected = options.get(which);
                    if (selected.startsWith("انتقال")) showTransferDialog();
                    else if (selected.startsWith("ادغام")) showMergeDialog();
                    else if (selected.startsWith("لغو")) showCancelDialog();
                    else showHardDeleteDialog();
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showTransferDialog() {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(this, "/api/tables");
                JSONArray tables = response.optJSONArray("tables");
                runOnUiThread(() -> {
                    List<Long> ids = new ArrayList<>();
                    List<String> names = new ArrayList<>();

                    if (tables != null) {
                        for (int i = 0; i < tables.length(); i++) {
                            JSONObject table = tables.optJSONObject(i);
                            if (table == null) continue;
                            boolean busy = table.optInt(
                                    "busy",
                                    table.optLong("order_id", 0L) > 0 ? 1 : 0
                            ) == 1;
                            if (!busy) {
                                ids.add(table.optLong("id"));
                                names.add(table.optString("name", "میز"));
                            }
                        }
                    }

                    if (ids.isEmpty()) {
                        showError("هیچ میز آزادی برای انتقال وجود ندارد.");
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("انتقال به میز")
                            .setItems(names.toArray(new String[0]), (d,which) ->
                                    transferOrder(ids.get(which), names.get(which)))
                            .setNegativeButton("لغو", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void transferOrder(long tableId, String newTableName) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("table_id", tableId);
                ApiClient.post(this, "/api/orders/" + orderId + "/transfer", body);
                runOnUiThread(() -> {
                    tableName = newTableName;
                    Toast.makeText(this, "سفارش منتقل شد.", Toast.LENGTH_LONG).show();
                    recreate();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void showMergeDialog() {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(this, "/api/tables");
                JSONArray tables = response.optJSONArray("tables");
                runOnUiThread(() -> {
                    List<Long> orderIds = new ArrayList<>();
                    List<String> names = new ArrayList<>();

                    if (tables != null) {
                        for (int i = 0; i < tables.length(); i++) {
                            JSONObject table = tables.optJSONObject(i);
                            if (table == null) continue;
                            long otherOrderId = table.optLong("order_id", 0L);
                            if (otherOrderId > 0 && otherOrderId != orderId) {
                                orderIds.add(otherOrderId);
                                names.add(table.optString("name", "میز") +
                                        " • سفارش #" + JalaliDateTime.fa(String.valueOf(otherOrderId)));
                            }
                        }
                    }

                    if (orderIds.isEmpty()) {
                        showError("سفارش باز دیگری برای ادغام وجود ندارد.");
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("ادغام با سفارش دیگر")
                            .setMessage("آیتم‌های سفارش انتخاب‌شده به این سفارش منتقل می‌شوند و میز آن آزاد خواهد شد.")
                            .setItems(names.toArray(new String[0]), (d,which) ->
                                    mergeOrder(orderIds.get(which)))
                            .setNegativeButton("لغو", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void mergeOrder(long sourceOrderId) {
        new AlertDialog.Builder(this)
                .setTitle("تأیید ادغام")
                .setMessage("دو سفارش با هم ادغام شوند؟ این عملیات در گزارش فعالیت ثبت می‌شود.")
                .setPositiveButton("ادغام", (d,w) -> {
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("source_order_id", sourceOrderId);
                            ApiClient.post(this, "/api/orders/" + orderId + "/merge", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "سفارش‌ها ادغام شدند.", Toast.LENGTH_LONG).show();
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

    private void showCancelDialog() {
        EditText reason = field("دلیل لغو سفارش", false);
        new AlertDialog.Builder(this)
                .setTitle("لغو سفارش")
                .setMessage("سفارش لغو می‌شود و میز آزاد خواهد شد. دلیل لغو در Audit Log ثبت می‌شود.")
                .setView(reason)
                .setPositiveButton("لغو سفارش", (d,w) -> {
                    String text = reason.getText().toString().trim();
                    if (text.length() < 3) {
                        showError("دلیل لغو را وارد کنید.");
                        return;
                    }
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("reason", text);
                            ApiClient.post(this, "/api/orders/" + orderId + "/cancel", body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "سفارش لغو شد.", Toast.LENGTH_LONG).show();
                                finish();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showReverseSettlementDialog() {
        EditText reason = field("دلیل برگرداندن تسویه", false);
        new AlertDialog.Builder(this)
                .setTitle("برگرداندن تسویه")
                .setMessage("پرداخت‌های این سفارش حذف و سفارش دوباره باز می‌شود. این عملیات ثبت می‌شود.")
                .setView(reason)
                .setPositiveButton("برگرداندن", (d,w) -> {
                    String text = reason.getText().toString().trim();
                    if (text.length() < 3) {
                        showError("دلیل را وارد کنید.");
                        return;
                    }
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("reason", text);
                            ApiClient.post(this,
                                    "/api/orders/" + orderId + "/reverse-settlement",
                                    body);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "تسویه برگشت داده شد و سفارش دوباره باز است.", Toast.LENGTH_LONG).show();
                                reload();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showHardDeleteDialog() {
        if (!isAdmin()) {
            showError("حذف کامل سفارش فقط برای مدیر مجاز است.");
            return;
        }

        EditText confirm = field("برای تأیید کلمه حذف را بنویس", false);
        new AlertDialog.Builder(this)
                .setTitle("حذف کامل سفارش")
                .setMessage("این عملیات سفارش، آیتم‌ها، پرداخت‌ها و نسیه متصل به آن را از دیتابیس حذف می‌کند و قابل بازگشت نیست.")
                .setView(confirm)
                .setPositiveButton("حذف کامل", (d,w) -> {
                    if (!"حذف".equals(confirm.getText().toString().trim())) {
                        showError("برای تأیید، کلمه «حذف» را وارد کنید.");
                        return;
                    }
                    new Thread(() -> {
                        try {
                            ApiClient.delete(this, "/api/orders/" + orderId);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "سفارش کامل حذف شد.", Toast.LENGTH_LONG).show();
                                finish();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showCatalogPicker(String catalogType) {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/catalog?type=" + catalogType
                );
                JSONArray items = response.optJSONArray("items");
                runOnUiThread(() -> renderCatalogPicker(catalogType, items));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderCatalogPicker(String catalogType, JSONArray items) {
        if (items == null || items.length() == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(menuTypeLabel(catalogType))
                    .setMessage("آیتم فعالی در این بخش منو تعریف نشده است.")
                    .setPositiveButton("باشه", null)
                    .setNeutralButton(
                            "مدیریت منو",
                            (d,w) -> {
                                if (!"staff".equals(role)) {
                                    startActivity(new Intent(this, CatalogActivity.class));
                                }
                            }
                    )
                    .show();
            return;
        }

        List<Long> categoryIds = new ArrayList<>();
        List<String> categoryNames = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            long categoryId = item.optLong("category_id", 0L);
            String categoryName = item.optString("category_name", "").trim();
            if (categoryName.isEmpty()) categoryName = "بدون دسته‌بندی";

            if (!categoryIds.contains(categoryId)) {
                categoryIds.add(categoryId);
                categoryNames.add(categoryName);
            }
        }

        if (categoryIds.size() <= 1) {
            renderCatalogItems(catalogType, items);
            return;
        }

        String[] labels = new String[categoryNames.size() + 1];
        labels[0] = "همه " + menuTypeLabel(catalogType) + "‌ها";
        for (int i = 0; i < categoryNames.size(); i++) {
            labels[i + 1] = categoryNames.get(i);
        }

        new AlertDialog.Builder(this)
                .setTitle("انتخاب دسته‌بندی " + menuTypeLabel(catalogType))
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        renderCatalogItems(catalogType, items);
                        return;
                    }

                    long selectedId = categoryIds.get(which - 1);
                    JSONArray filtered = new JSONArray();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item != null && item.optLong("category_id", 0L) == selectedId) {
                            filtered.put(item);
                        }
                    }
                    renderCatalogItems(catalogType, filtered);
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void renderCatalogItems(String catalogType, JSONArray items) {
        int count = items == null ? 0 : items.length();
        boolean staff = "staff".equals(role);
        int offset = staff ? 0 : 1;
        String[] labels = new String[count + offset];

        if (!staff) labels[0] = "+ ثبت دستی";

        for (int i = 0; i < count; i++) {
            JSONObject item = items.optJSONObject(i);
            String category = item == null ? "" : item.optString("category_name", "").trim();
            String prefix = category.isEmpty() ? "" : category + " • ";
            labels[i + offset] = item == null
                    ? "مورد"
                    : prefix + item.optString("name", "مورد") +
                    " • " + money(item.optLong("price", 0L));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("انتخاب " + menuTypeLabel(catalogType))
                .setItems(labels, (dialog, which) -> {
                    if (!staff && which == 0) {
                        showAddItemDialog(catalogType);
                        return;
                    }
                    int index = which - offset;
                    JSONObject item = items == null ? null : items.optJSONObject(index);
                    if (item != null) showCatalogItemQtyDialog(catalogType, item);
                })
                .setNegativeButton("لغو", null);

        if (!staff) {
            builder.setNeutralButton(
                    "مدیریت منو",
                    (dialog, which) ->
                            startActivity(new Intent(this, CatalogActivity.class))
            );
        }

        builder.show();
    }

    private void showCatalogItemQtyDialog(String catalogType, JSONObject item) {
        String name = item.optString("name", menuTypeLabel(catalogType));
        long priceValue = item.optLong("price", 0L);
        long catalogId = item.optLong("id", 0L);

        LinearLayout box = dialogBox();

        String category = item.optString("category_name", "").trim();
        TextView info = text(
                (category.isEmpty() ? "" : category + "\n") +
                        name + "\nقیمت: " + money(priceValue),
                14, ink, true
        );
        info.setGravity(Gravity.RIGHT);
        info.setPadding(0, 0, 0, dp(10));
        box.addView(info);

        EditText qty = numberField("تعداد", "1");
        box.addView(qty);

        new AlertDialog.Builder(this)
                .setTitle("افزودن " + menuTypeLabel(catalogType))
                .setView(box)
                .setPositiveButton("افزودن", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("catalog_type", catalogType);
                        body.put("catalog_id", catalogId);
                        body.put("qty", Math.max(1, parseLong(qty.getText().toString())));
                        ApiClient.post(this, "/api/orders/" + orderId + "/items", body);

                        runOnUiThread(() -> {
                            Toast.makeText(this, "به سفارش اضافه شد.", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showAddItemDialog(String catalogType) {
        if ("staff".equals(role)) {
            Toast.makeText(
                    this,
                    "برای حساب شاگرد فقط اقلام تعریف‌شده منو قابل فروش هستند.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        LinearLayout box = dialogBox();
        EditText name = field("نام " + menuTypeLabel(catalogType), false);
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
                .setTitle("ثبت دستی " + menuTypeLabel(catalogType))
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("item_type", "hookah".equals(catalogType) ? "hookah" : "service");
                        body.put("catalog_kind", catalogType);
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
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private String menuTypeLabel(String value) {
        if ("hookah".equals(value)) return "قلیان";
        if ("drink".equals(value)) return "نوشیدنی";
        if ("food".equals(value)) return "خوراکی";
        return "خدمت";
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
        if (!PermissionStore.has(this, "apply_discount")) {
            discount.setEnabled(false);
            discount.setAlpha(0.55f);
        }
        EditText cash = numberField("مبلغ نقدی", "0");
        EditText card = numberField("مبلغ کارت / کارتخوان", "0");
        EditText transfer = numberField("مبلغ کارت‌به‌کارت", "0");
        EditText credit = numberField("مبلغ نسیه", "0");

        addLabeledNumberField(
                box,
                "تخفیف",
                PermissionStore.has(this, "apply_discount")
                        ? "از مبلغ کل کم می‌شود"
                        : "مجوز ثبت تخفیف برای این نقش فعال نیست",
                discount
        );
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

                long balance = c.optLong("balance", 0L);
                long limit = c.optLong("credit_limit", 0L);
                long remaining = c.optLong("remaining_credit", 0L);
                long overdue = c.optLong("overdue_amount", 0L);

                String label = c.optString("name", "مشتری") +
                        " • بدهی " + money(balance);

                if (limit > 0) {
                    label += " • اعتبار آزاد " + money(remaining);
                } else {
                    label += " • بدون سقف مشخص";
                }

                if (overdue > 0) {
                    label += " • معوق";
                }

                customerNames.add(label);
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

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(turquoise);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        GradientDrawable bgDrawable = rounded(Color.WHITE, 16);
        bgDrawable.setStroke(dp(1), turquoise);
        b.setBackground(bgDrawable);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        lp.setMargins(0, dp(4), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private Button dangerButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(red);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        GradientDrawable bgDrawable = rounded(Color.rgb(250,235,232), 16);
        bgDrawable.setStroke(dp(1), red);
        b.setBackground(bgDrawable);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        lp.setMargins(0, dp(4), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
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
