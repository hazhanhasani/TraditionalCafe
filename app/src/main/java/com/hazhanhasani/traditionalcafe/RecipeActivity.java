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

public class RecipeActivity extends Activity {

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

    private long catalogId;
    private String catalogType = "hookah";
    private String catalogName = "قلیان";

    private LinearLayout content;
    private ProgressBar loading;

    private final List<JSONObject> ingredients = new ArrayList<>();
    private final List<JSONObject> inventoryItems = new ArrayList<>();
    private JSONObject recipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PermissionStore.has(this, "manage_inventory")) {
            Toast.makeText(this, "مجوز مدیریت فرمول مصرف برای این حساب فعال نیست.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        catalogId = getIntent().getLongExtra("catalog_id", 0L);
        catalogType = getIntent().getStringExtra("catalog_type");
        if (catalogType == null || catalogType.trim().isEmpty()) catalogType = "hookah";
        catalogName = getIntent().getStringExtra("catalog_name");
        if (catalogName == null || catalogName.trim().isEmpty()) catalogName = "قلیان";

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        reload();
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

        TextView title = text("فرمول مصرف", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(catalogName, 12, brown, true);
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

        Button add = primaryButton("+ ماده اولیه");
        add.setOnClickListener(v -> showIngredientDialog(null));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button save = secondaryButton("ذخیره فرمول");
        save.setOnClickListener(v -> saveRecipe());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        saveLp.setMarginStart(dp(8));
        actions.addView(save, saveLp);

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
                JSONObject recipeResponse = ApiClient.get(
                        this,
                        "/api/recipes/" + catalogType + "/" + catalogId
                );
                JSONObject inventoryResponse = ApiClient.get(this, "/api/inventory");

                JSONObject loadedRecipe = recipeResponse.optJSONObject("recipe");
                JSONArray loadedIngredients = loadedRecipe == null
                        ? null
                        : loadedRecipe.optJSONArray("ingredients");
                JSONArray loadedInventory = inventoryResponse.optJSONArray("items");

                ingredients.clear();
                inventoryItems.clear();

                if (loadedIngredients != null) {
                    for (int i = 0; i < loadedIngredients.length(); i++) {
                        JSONObject row = loadedIngredients.optJSONObject(i);
                        if (row != null) ingredients.add(new JSONObject(row.toString()));
                    }
                }

                if (loadedInventory != null) {
                    for (int i = 0; i < loadedInventory.length(); i++) {
                        JSONObject row = loadedInventory.optJSONObject(i);
                        if (row != null && row.optInt("active", 1) == 1) {
                            inventoryItems.add(new JSONObject(row.toString()));
                        }
                    }
                }

                recipe = loadedRecipe;
                runOnUiThread(this::render);
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render() {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        renderSummary();

        TextView section = text("مواد اولیه فرمول", 17, ink, true);
        section.setGravity(Gravity.RIGHT);
        section.setPadding(dp(2), dp(18), dp(2), dp(10));
        content.addView(section);

        if (ingredients.isEmpty()) {
            TextView empty = text(
                    "هنوز فرمولی تعریف نشده است. مثلاً ۱۵ گرم تنباکو + ۳ عدد زغال را اضافه کن.",
                    12, muted, false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(26), dp(10), dp(26));
            content.addView(empty);
            return;
        }

        for (int i = 0; i < ingredients.size(); i++) {
            final int index = i;
            JSONObject row = ingredients.get(i);

            LinearLayout card = card();

            TextView name = text(row.optString("inventory_name", "ماده اولیه"), 15, ink, true);
            name.setGravity(Gravity.RIGHT);
            card.addView(name);

            String unit = row.optString("unit", "واحد");
            long qty = row.optLong("qty_per_unit", 0L);
            long stock = row.optLong("stock_qty", 0L);
            long purchasePrice = row.optLong("purchase_price", 0L);
            long componentCost = qty * purchasePrice;
            long possible = qty > 0 ? stock / qty : 0;

            TextView meta = text(
                    "مصرف هر سرو: " + number(qty) + " " + unit +
                            "\nموجودی: " + number(stock) + " " + unit +
                            "  •  قابل تهیه: " + number(possible) + " سرو" +
                            "\nهزینه این جزء: " + money(componentCost),
                    11,
                    possible <= 0 ? red : muted,
                    possible <= 0
            );
            meta.setGravity(Gravity.RIGHT);
            meta.setPadding(0, dp(6), 0, 0);
            card.addView(meta);

            card.setOnClickListener(v -> showIngredientActions(index));
            content.addView(card);
        }
    }

    private void renderSummary() {
        LinearLayout card = card();

        long price = recipe == null ? 0L : recipe.optLong("price", 0L);
        long recipeCost = calculateLocalCost();
        long profit = price - recipeCost;
        long canMake = calculateLocalCanMake();
        int low = calculateLowComponents();

        TextView title = text("خلاصه فرمول", 17, ink, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        addSummaryRow(card, "قیمت فروش", money(price), turquoise);
        addSummaryRow(card, "هزینه واقعی مواد", money(recipeCost), brown);
        addSummaryRow(card, "سود خام هر سرو", money(profit), profit >= 0 ? green : red);
        addSummaryRow(
                card,
                "حداکثر قابل سرو با موجودی فعلی",
                ingredients.isEmpty() ? "فرمول ندارد" : number(canMake) + " سرو",
                canMake > 0 ? green : red
        );

        if (low > 0) {
            TextView warning = text(
                    "هشدار: " + number(low) + " ماده اولیه کم یا تمام شده است.",
                    11, red, true
            );
            warning.setGravity(Gravity.RIGHT);
            warning.setPadding(0, dp(8), 0, 0);
            card.addView(warning);
        }

        TextView note = text(
                "با ذخیره فرمول، هزینه تمام‌شده قلیان از قیمت خرید مواد اولیه محاسبه می‌شود. هنگام تسویه فروش، همین مقادیر به‌صورت خودکار از انبار کم می‌شوند.",
                10, muted, false
        );
        note.setGravity(Gravity.RIGHT);
        note.setPadding(0, dp(10), 0, 0);
        card.addView(note);

        content.addView(card);
    }

    private void addSummaryRow(LinearLayout parent, String label, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView left = text(label, 11, muted, false);
        left.setGravity(Gravity.RIGHT);
        row.addView(left, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView right = text(value, 12, color, true);
        right.setGravity(Gravity.LEFT);
        row.addView(right);

        parent.addView(row);
    }

    private void showIngredientActions(int index) {
        JSONObject row = ingredients.get(index);
        new AlertDialog.Builder(this)
                .setTitle(row.optString("inventory_name", "ماده اولیه"))
                .setItems(new String[]{"تغییر مقدار مصرف", "حذف از فرمول"}, (d,which) -> {
                    if (which == 0) showIngredientDialog(index);
                    else {
                        ingredients.remove(index);
                        render();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showIngredientDialog(Integer editingIndex) {
        if (inventoryItems.isEmpty()) {
            showError("ابتدا از بخش انبار حداقل یک کالای فعال تعریف کن.");
            return;
        }

        LinearLayout box = dialogBox();

        Spinner inventory = new Spinner(this);
        String[] labels = new String[inventoryItems.size()];
        for (int i = 0; i < inventoryItems.size(); i++) {
            JSONObject row = inventoryItems.get(i);
            labels[i] = row.optString("name", "کالا") +
                    " • موجودی " + number(row.optLong("stock_qty", 0L)) +
                    " " + row.optString("unit", "واحد");
        }
        inventory.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                labels
        ));

        EditText qty = numberField("مقدار مصرف در هر سرو", "1");

        if (editingIndex != null) {
            JSONObject current = ingredients.get(editingIndex);
            long currentId = current.optLong("inventory_item_id", 0L);
            for (int i = 0; i < inventoryItems.size(); i++) {
                if (inventoryItems.get(i).optLong("id") == currentId) {
                    inventory.setSelection(i);
                    break;
                }
            }
            inventory.setEnabled(false);
            qty.setText(String.valueOf(current.optLong("qty_per_unit", 1L)));
        }

        TextView stockLabel = label("ماده اولیه");
        box.addView(stockLabel);
        box.addView(inventory, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));
        box.addView(label("مقدار مصرف"));
        box.addView(qty);

        new AlertDialog.Builder(this)
                .setTitle(editingIndex == null ? "ماده اولیه جدید" : "ویرایش مقدار مصرف")
                .setView(box)
                .setPositiveButton("ثبت", (d,w) -> {
                    long qtyValue = parseLong(qty.getText().toString());
                    if (qtyValue <= 0) {
                        showError("مقدار مصرف باید بیشتر از صفر باشد.");
                        return;
                    }

                    JSONObject selected = inventoryItems.get(inventory.getSelectedItemPosition());
                    long selectedId = selected.optLong("id");

                    if (editingIndex == null) {
                        for (JSONObject row : ingredients) {
                            if (row.optLong("inventory_item_id") == selectedId) {
                                showError("این ماده اولیه قبلاً در فرمول وجود دارد.");
                                return;
                            }
                        }
                    }

                    try {
                        JSONObject ingredient = editingIndex == null
                                ? new JSONObject()
                                : ingredients.get(editingIndex);

                        ingredient.put("inventory_item_id", selectedId);
                        ingredient.put("inventory_name", selected.optString("name", "کالا"));
                        ingredient.put("unit", selected.optString("unit", "واحد"));
                        ingredient.put("stock_qty", selected.optLong("stock_qty", 0L));
                        ingredient.put("min_stock", selected.optLong("min_stock", 0L));
                        ingredient.put("purchase_price", selected.optLong("purchase_price", 0L));
                        ingredient.put("qty_per_unit", qtyValue);

                        if (editingIndex == null) ingredients.add(ingredient);
                        render();
                    } catch (Exception e) {
                        showError(e.getMessage());
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void saveRecipe() {
        new Thread(() -> {
            try {
                JSONArray rows = new JSONArray();
                for (JSONObject ingredient : ingredients) {
                    JSONObject item = new JSONObject();
                    item.put("inventory_item_id", ingredient.optLong("inventory_item_id"));
                    item.put("qty_per_unit", ingredient.optLong("qty_per_unit"));
                    rows.put(item);
                }

                JSONObject body = new JSONObject();
                body.put("ingredients", rows);

                JSONObject response = ApiClient.patch(
                        this,
                        "/api/recipes/" + catalogType + "/" + catalogId,
                        body
                );

                recipe = response.optJSONObject("recipe");
                JSONArray serverIngredients = recipe == null
                        ? null
                        : recipe.optJSONArray("ingredients");

                ingredients.clear();
                if (serverIngredients != null) {
                    for (int i = 0; i < serverIngredients.length(); i++) {
                        JSONObject row = serverIngredients.optJSONObject(i);
                        if (row != null) ingredients.add(new JSONObject(row.toString()));
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(
                            this,
                            "فرمول ذخیره شد و هزینه تمام‌شده به‌روزرسانی شد.",
                            Toast.LENGTH_LONG
                    ).show();
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private long calculateLocalCost() {
        long total = 0L;
        for (JSONObject row : ingredients) {
            total += row.optLong("qty_per_unit", 0L) * row.optLong("purchase_price", 0L);
        }
        return total;
    }

    private long calculateLocalCanMake() {
        if (ingredients.isEmpty()) return 0L;
        long possible = Long.MAX_VALUE;
        for (JSONObject row : ingredients) {
            long qty = row.optLong("qty_per_unit", 0L);
            long stock = row.optLong("stock_qty", 0L);
            if (qty <= 0) return 0L;
            possible = Math.min(possible, stock / qty);
        }
        return possible == Long.MAX_VALUE ? 0L : possible;
    }

    private int calculateLowComponents() {
        int count = 0;
        for (JSONObject row : ingredients) {
            long stock = row.optLong("stock_qty", 0L);
            long min = row.optLong("min_stock", 0L);
            long qty = row.optLong("qty_per_unit", 0L);
            if (stock <= min || qty <= 0 || stock / qty <= 0) count++;
        }
        return count;
    }

    private TextView label(String value) {
        TextView view = text(value, 11, muted, true);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(dp(4), dp(7), dp(4), dp(4));
        return view;
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

    private EditText numberField(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(Color.rgb(250,248,244), 14));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.bottomMargin = dp(9);
        input.setLayoutParams(lp);
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
