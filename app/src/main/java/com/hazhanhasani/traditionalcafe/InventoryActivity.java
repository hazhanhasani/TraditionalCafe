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
import android.widget.AdapterView;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InventoryActivity extends Activity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PermissionStore.has(this, "manage_inventory")) {
            Toast.makeText(this, "مجوز مدیریت انبار برای این حساب فعال نیست.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

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
        top.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView title = text("انبار و موجودی", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text("موجودی، خرید، خروج و اتصال خودکار به فروش", 11, muted, false);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(16), dp(4), dp(16), dp(10));

        Button add = primaryButton("+ کالای انبار");
        add.setOnClickListener(v -> showItemDialog(null));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button links = secondaryButton("اتصال به فروش");
        links.setOnClickListener(v -> showLinks());
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        linkLp.setMarginStart(dp(8));
        actions.addView(links, linkLp);

        root.addView(actions);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadingLp);

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
                JSONObject response = ApiClient.get(this, "/api/inventory?all=1");
                JSONArray items = response.optJSONArray("items");
                JSONObject summary = response.optJSONObject("summary");
                runOnUiThread(() -> render(items, summary));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONArray items, JSONObject summary) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        renderSummary(summary);

        TextView section = text("کالاهای انبار", 17, ink, true);
        section.setGravity(Gravity.RIGHT);
        section.setPadding(dp(2), dp(18), dp(2), dp(10));
        content.addView(section);

        if (items == null || items.length() == 0) {
            showEmpty("هنوز کالایی در انبار تعریف نشده است.");
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            LinearLayout card = card();

            LinearLayout header = new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            header.addView(right, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ));

            TextView name = text(item.optString("name", "کالا"), 16, ink, true);
            name.setGravity(Gravity.RIGHT);
            right.addView(name);

            String unit = item.optString("unit", "عدد");
            long stock = item.optLong("stock_qty", 0L);
            long min = item.optLong("min_stock", 0L);
            long price = item.optLong("purchase_price", 0L);

            TextView details = text(
                    "موجودی: " + number(stock) + " " + unit +
                            "  •  حداقل: " + number(min) + " " + unit +
                            "\nآخرین قیمت خرید: " + money(price),
                    11, muted, false
            );
            details.setGravity(Gravity.RIGHT);
            details.setPadding(0, dp(5), 0, 0);
            right.addView(details);

            boolean active = item.optInt("active", 1) == 1;
            String status = item.optString("stock_status", "ok");
            String badgeText;
            int badgeColor;
            int badgeBg;
            if (!active) {
                badgeText = "غیرفعال";
                badgeColor = muted;
                badgeBg = Color.rgb(239,236,232);
            } else if ("out".equals(status)) {
                badgeText = "تمام شده";
                badgeColor = red;
                badgeBg = Color.rgb(250,235,232);
            } else if ("low".equals(status)) {
                badgeText = "رو به اتمام";
                badgeColor = brown;
                badgeBg = softGold;
            } else {
                badgeText = "موجود";
                badgeColor = green;
                badgeBg = Color.rgb(232,243,235);
            }

            TextView badge = text(badgeText, 10, badgeColor, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(9), dp(6), dp(9), dp(6));
            badge.setBackground(rounded(badgeBg, 14));
            header.addView(badge);

            card.addView(header);
            card.setOnClickListener(v -> showItemActions(item));
            content.addView(card);
        }
    }

    private void renderSummary(JSONObject summary) {
        long total = summary == null ? 0L : summary.optLong("total_items", 0L);
        long active = summary == null ? 0L : summary.optLong("active_items", 0L);
        long low = summary == null ? 0L : summary.optLong("low_stock_items", 0L);
        long value = summary == null ? 0L : summary.optLong("inventory_value", 0L);

        LinearLayout card = card();

        TextView title = text("وضعیت انبار", 17, ink, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        TextView valueView = text("ارزش تقریبی موجودی: " + money(value), 14, turquoise, true);
        valueView.setGravity(Gravity.RIGHT);
        valueView.setPadding(0, dp(8), 0, dp(7));
        card.addView(valueView);

        TextView meta = text(
                "کل کالا: " + number(total) +
                        "  •  فعال: " + number(active) +
                        "  •  کمبود موجودی: " + number(low),
                11, low > 0 ? red : muted, low > 0
        );
        meta.setGravity(Gravity.RIGHT);
        card.addView(meta);

        if (low > 0) {
            TextView warning = text(
                    "هشدار: " + number(low) + " قلم به حداقل موجودی رسیده یا تمام شده است.",
                    11, red, true
            );
            warning.setGravity(Gravity.RIGHT);
            warning.setPadding(0, dp(8), 0, 0);
            card.addView(warning);
        }

        content.addView(card);
    }

    private void showItemActions(JSONObject item) {
        String[] options = new String[]{
                "ثبت خرید / ورودی",
                "ثبت خروج / ضایعات",
                "ویرایش مشخصات",
                "گردش موجودی"
        };

        new AlertDialog.Builder(this)
                .setTitle(item.optString("name", "کالا"))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showMovementDialog(item, true);
                    else if (which == 1) showMovementDialog(item, false);
                    else if (which == 2) showItemDialog(item);
                    else showMovements(item);
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showItemDialog(JSONObject existing) {
        boolean editing = existing != null;
        LinearLayout box = dialogBox();

        EditText name = field("نام کالا؛ مثلاً تنباکو دوسیب", false);
        EditText unit = field("واحد؛ مثلاً گرم، عدد، بسته، لیتر", false);
        EditText opening = numberField("موجودی اولیه", "0");
        EditText min = numberField("حداقل موجودی برای هشدار", "0");
        EditText purchasePrice = numberField("قیمت خرید هر واحد (تومان)", "0");

        CheckBox active = new CheckBox(this);
        active.setText("فعال باشد");
        active.setTextColor(ink);
        active.setChecked(true);

        if (editing) {
            name.setText(existing.optString("name", ""));
            unit.setText(existing.optString("unit", "عدد"));
            opening.setVisibility(View.GONE);
            min.setText(String.valueOf(existing.optLong("min_stock", 0L)));
            purchasePrice.setText(String.valueOf(existing.optLong("purchase_price", 0L)));
            active.setChecked(existing.optInt("active", 1) == 1);
        }

        box.addView(name);
        box.addView(unit);
        box.addView(opening);
        box.addView(min);
        box.addView(purchasePrice);
        if (editing) box.addView(active);

        new AlertDialog.Builder(this)
                .setTitle(editing ? "ویرایش کالای انبار" : "کالای جدید")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("name", name.getText().toString().trim());
                        body.put("unit", unit.getText().toString().trim());
                        body.put("min_stock", parseLong(min.getText().toString()));
                        body.put("purchase_price", parseLong(purchasePrice.getText().toString()));

                        if (editing) {
                            body.put("active", active.isChecked());
                            ApiClient.patch(this, "/api/inventory/" + existing.optLong("id"), body);
                        } else {
                            body.put("opening_stock", parseLong(opening.getText().toString()));
                            ApiClient.post(this, "/api/inventory", body);
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(this, "اطلاعات انبار ذخیره شد.", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showMovementDialog(JSONObject item, boolean incoming) {
        LinearLayout box = dialogBox();

        TextView stock = text(
                "موجودی فعلی: " + number(item.optLong("stock_qty", 0L)) +
                        " " + item.optString("unit", "عدد"),
                12, muted, false
        );
        stock.setGravity(Gravity.RIGHT);
        stock.setPadding(dp(4), 0, dp(4), dp(8));
        box.addView(stock);

        Spinner type = new Spinner(this);
        String[] types = incoming
                ? new String[]{"خرید جدید", "اصلاح افزایشی"}
                : new String[]{"خروج دستی", "ضایعات"};
        type.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                types
        ));

        EditText qty = numberField(
                "مقدار (" + item.optString("unit", "عدد") + ")",
                "1"
        );
        EditText cost = numberField(
                "قیمت خرید هر واحد (تومان)",
                String.valueOf(item.optLong("purchase_price", 0L))
        );
        EditText note = field("توضیحات؛ اختیاری", false);

        box.addView(type, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));
        box.addView(qty);
        if (incoming) box.addView(cost);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle(incoming ? "ثبت ورودی انبار" : "ثبت خروج انبار")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> new Thread(() -> {
                    try {
                        String movementType;
                        if (incoming) {
                            movementType = type.getSelectedItemPosition() == 0
                                    ? "purchase" : "adjustment_in";
                        } else {
                            movementType = type.getSelectedItemPosition() == 0
                                    ? "adjustment_out" : "waste";
                        }

                        JSONObject body = new JSONObject();
                        body.put("type", movementType);
                        body.put("qty", parseLong(qty.getText().toString()));
                        body.put("unit_cost", parseLong(cost.getText().toString()));
                        body.put("note", note.getText().toString().trim());

                        ApiClient.post(
                                this,
                                "/api/inventory/" + item.optLong("id") + "/movements",
                                body
                        );

                        runOnUiThread(() -> {
                            Toast.makeText(this, "گردش انبار ثبت شد.", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showMovements(JSONObject item) {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/inventory/" + item.optLong("id") + "/movements"
                );
                JSONArray rows = response.optJSONArray("movements");
                runOnUiThread(() -> renderMovementsDialog(item, rows));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderMovementsDialog(JSONObject item, JSONArray rows) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(8), dp(14), dp(14));

        int count = rows == null ? 0 : Math.min(rows.length(), 100);
        if (count == 0) {
            TextView empty = text("گردشی برای این کالا ثبت نشده است.", 12, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            list.addView(empty);
        }

        for (int i = 0; i < count; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            long delta = row.optLong("qty_delta", 0L);
            String type = movementLabel(row.optString("movement_type", ""));
            String unit = item.optString("unit", "عدد");

            LinearLayout card = card();
            TextView title = text(
                    type + " • " + (delta > 0 ? "+" : "") + number(delta) + " " + unit,
                    13, delta >= 0 ? green : red, true
            );
            title.setGravity(Gravity.RIGHT);
            card.addView(title);

            String note = row.optString("note", "");
            String created = row.optString("created_at", "");
            String by = row.optString("created_by_name", "");

            TextView meta = text(
                    (note.isEmpty() ? "" : note + "\n") +
                            (by.isEmpty() ? "" : "ثبت‌کننده: " + by + "\n") +
                            (created.isEmpty() ? "" : JalaliDateTime.formatUtcCompact(created)),
                    10, muted, false
            );
            meta.setGravity(Gravity.RIGHT);
            meta.setPadding(0, dp(5), 0, 0);
            card.addView(meta);
            list.addView(card);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        new AlertDialog.Builder(this)
                .setTitle("گردش • " + item.optString("name", "کالا"))
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .show();
    }

    private void showLinks() {
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject inventory = ApiClient.get(this, "/api/inventory");
                JSONObject hookahs = ApiClient.get(this, "/api/catalog?type=hookah");
                JSONObject drinks = ApiClient.get(this, "/api/catalog?type=drink");
                JSONObject foods = ApiClient.get(this, "/api/catalog?type=food");
                JSONObject services = ApiClient.get(this, "/api/catalog?type=service");
                JSONObject links = ApiClient.get(this, "/api/inventory-links");

                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    renderLinksDialog(
                            inventory.optJSONArray("items"),
                            hookahs.optJSONArray("items"),
                            drinks.optJSONArray("items"),
                            foods.optJSONArray("items"),
                            services.optJSONArray("items"),
                            links.optJSONArray("links")
                    );
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderLinksDialog(
            JSONArray inventory,
            JSONArray hookahs,
            JSONArray drinks,
            JSONArray foods,
            JSONArray services,
            JSONArray links
    ) {
        LinearLayout root = dialogBox();

        TextView help = text(
                "اینجا مشخص می‌کنی فروش هر قلیان یا خدمت چه مقدار از یک کالای انبار کم کند. فرمول چندماده‌ای را می‌توان با چند اتصال برای یک آیتم ساخت.",
                11, muted, false
        );
        help.setGravity(Gravity.RIGHT);
        help.setPadding(dp(4), 0, dp(4), dp(10));
        root.addView(help);

        Button add = primaryButton("+ اتصال جدید");
        add.setOnClickListener(v -> showAddLinkDialog(
                inventory, hookahs, drinks, foods, services
        ));
        root.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ));

        TextView title = text("اتصال‌های فعلی", 14, ink, true);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(dp(2), dp(14), dp(2), dp(8));
        root.addView(title);

        if (links == null || links.length() == 0) {
            TextView empty = text("هنوز اتصالی تعریف نشده است.", 12, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(6), dp(18), dp(6), dp(18));
            root.addView(empty);
        } else {
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.optJSONObject(i);
                if (link == null) continue;

                LinearLayout card = card();
                String typeFa = catalogTypeLabel(link.optString("catalog_type"));

                TextView line = text(
                        typeFa + " «" + link.optString("catalog_name", "مورد") + "»" +
                                "\n" + number(link.optLong("qty_per_unit", 0L)) + " " +
                                link.optString("unit", "عدد") +
                                " از «" + link.optString("inventory_name", "انبار") + "»",
                        12, ink, true
                );
                line.setGravity(Gravity.RIGHT);
                card.addView(line);

                TextView remove = text("حذف اتصال", 11, red, true);
                remove.setGravity(Gravity.RIGHT);
                remove.setPadding(0, dp(8), 0, 0);
                remove.setOnClickListener(v -> confirmDeleteLink(link.optLong("id")));
                card.addView(remove);

                root.addView(card);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        new AlertDialog.Builder(this)
                .setTitle("اتصال فروش به انبار")
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .show();
    }

    private void showAddLinkDialog(
            JSONArray inventory,
            JSONArray hookahs,
            JSONArray drinks,
            JSONArray foods,
            JSONArray services
    ) {
        if (inventory == null || inventory.length() == 0) {
            showError("ابتدا حداقل یک کالای فعال در انبار تعریف کن.");
            return;
        }

        LinearLayout box = dialogBox();

        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"قلیان", "نوشیدنی", "خوراکی", "خدمت"}
        ));

        Spinner catalog = new Spinner(this);
        Spinner stock = new Spinner(this);

        List<JSONObject> stockRows = jsonList(inventory);
        stock.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names(stockRows, false)
        ));

        final List<JSONObject>[] catalogRows = new List[]{jsonList(hookahs)};
        setCatalogAdapter(catalog, catalogRows[0]);

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) catalogRows[0] = jsonList(hookahs);
                else if (position == 1) catalogRows[0] = jsonList(drinks);
                else if (position == 2) catalogRows[0] = jsonList(foods);
                else catalogRows[0] = jsonList(services);
                setCatalogAdapter(catalog, catalogRows[0]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        EditText qty = numberField("مصرف به ازای هر فروش", "1");

        box.addView(label("بخش منو"));
        box.addView(type, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ));
        box.addView(label("آیتم فروش"));
        box.addView(catalog, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ));
        box.addView(label("کالای انبار"));
        box.addView(stock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        ));
        box.addView(qty);

        new AlertDialog.Builder(this)
                .setTitle("اتصال جدید")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        if (catalogRows[0].isEmpty()) {
                            throw new IllegalStateException("در این بخش آیتم فعالی وجود ندارد.");
                        }

                        JSONObject selectedCatalog = catalogRows[0].get(
                                catalog.getSelectedItemPosition()
                        );
                        JSONObject selectedStock = stockRows.get(
                                stock.getSelectedItemPosition()
                        );

                        String catalogType;
                        if (type.getSelectedItemPosition() == 0) catalogType = "hookah";
                        else if (type.getSelectedItemPosition() == 1) catalogType = "drink";
                        else if (type.getSelectedItemPosition() == 2) catalogType = "food";
                        else catalogType = "service";

                        JSONObject body = new JSONObject();
                        body.put("catalog_type", catalogType);
                        body.put("catalog_id", selectedCatalog.optLong("id"));
                        body.put("inventory_item_id", selectedStock.optLong("id"));
                        body.put("qty_per_unit", parseLong(qty.getText().toString()));

                        ApiClient.post(this, "/api/inventory-links", body);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    this,
                                    "اتصال فروش به انبار ثبت شد.",
                                    Toast.LENGTH_LONG
                            ).show();
                            showLinks();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private String catalogTypeLabel(String value) {
        if ("hookah".equals(value)) return "قلیان";
        if ("drink".equals(value)) return "نوشیدنی";
        if ("food".equals(value)) return "خوراکی";
        return "خدمت";
    }

    private void confirmDeleteLink(long linkId) {
        new AlertDialog.Builder(this)
                .setTitle("حذف اتصال")
                .setMessage("این اتصال حذف شود؟ فروش‌های بعدی دیگر از این اتصال موجودی کم نمی‌کنند.")
                .setPositiveButton("حذف", (d,w) -> new Thread(() -> {
                    try {
                        ApiClient.delete(this, "/api/inventory-links/" + linkId);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "اتصال حذف شد.", Toast.LENGTH_SHORT).show();
                            showLinks();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private List<JSONObject> jsonList(JSONArray array) {
        List<JSONObject> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row != null) result.add(row);
        }
        return result;
    }

    private String[] names(List<JSONObject> rows, boolean includePrice) {
        String[] result = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.get(i);
            result[i] = row.optString("name", "مورد");
            if (includePrice) result[i] += " • " + money(row.optLong("price", 0L));
        }
        return result;
    }

    private void setCatalogAdapter(Spinner spinner, List<JSONObject> rows) {
        spinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                names(rows, true)
        ));
    }

    private TextView label(String value) {
        TextView view = text(value, 11, muted, true);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(dp(4), dp(7), dp(4), dp(4));
        return view;
    }

    private String movementLabel(String type) {
        if ("opening".equals(type)) return "موجودی اولیه";
        if ("purchase".equals(type)) return "خرید";
        if ("adjustment_in".equals(type)) return "اصلاح افزایشی";
        if ("adjustment_out".equals(type)) return "خروج دستی";
        if ("sale".equals(type)) return "مصرف فروش";
        if ("sale_reverse".equals(type)) return "بازگشت فروش";
        if ("waste".equals(type)) return "ضایعات";
        return type;
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
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(turquoise, 16));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(brown);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(softGold, 16));
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create(
                "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
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

    private void showEmpty(String message) {
        TextView empty = text(message, 13, muted, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(10), dp(30), dp(10), dp(30));
        content.addView(empty);
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
