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

public class CatalogActivity extends Activity {

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

    private String type = "hookah";
    private LinearLayout content;
    private ProgressBar loading;
    private Button hookahTab;
    private Button drinkTab;
    private Button foodTab;
    private Button serviceTab;
    private Button addButton;
    private Button categoriesButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PermissionStore.has(this, "manage_catalog")) {
            Toast.makeText(this, "مجوز مدیریت منو برای این حساب فعال نیست.", Toast.LENGTH_LONG).show();
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

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text("مدیریت منو", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titleBox.addView(title);

        TextView subtitle = text("قلیان، نوشیدنی، خوراکی، خدمات و دسته‌بندی‌ها", 11, muted, false);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titleBox.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.VERTICAL);
        tabs.setPadding(dp(16), dp(4), dp(16), dp(10));

        LinearLayout row1 = new LinearLayout(this);
        LinearLayout row2 = new LinearLayout(this);

        hookahTab = tabButton("قلیان");
        drinkTab = tabButton("نوشیدنی");
        foodTab = tabButton("خوراکی");
        serviceTab = tabButton("خدمات");

        hookahTab.setOnClickListener(v -> switchType("hookah"));
        drinkTab.setOnClickListener(v -> switchType("drink"));
        foodTab.setOnClickListener(v -> switchType("food"));
        serviceTab.setOnClickListener(v -> switchType("service"));

        row1.addView(hookahTab, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams drinkLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        drinkLp.setMarginStart(dp(8));
        row1.addView(drinkTab, drinkLp);

        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        row2Lp.topMargin = dp(8);
        row2.setLayoutParams(row2Lp);

        row2.addView(foodTab, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams serviceLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        serviceLp.setMarginStart(dp(8));
        row2.addView(serviceTab, serviceLp);

        tabs.addView(row1);
        tabs.addView(row2);
        root.addView(tabs);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(16), 0, dp(16), dp(10));

        addButton = primaryButton("+ قلیان جدید");
        addButton.setOnClickListener(v -> prepareEditDialog(null));
        actions.addView(addButton, new LinearLayout.LayoutParams(0, dp(50), 1f));

        categoriesButton = secondaryButton("دسته‌بندی‌ها");
        categoriesButton.setOnClickListener(v -> showCategoryManager());
        LinearLayout.LayoutParams categoryLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        categoryLp.setMarginStart(dp(8));
        actions.addView(categoriesButton, categoryLp);

        root.addView(actions);

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

        updateTabs();
        return root;
    }

    private void switchType(String value) {
        type = value;
        updateTabs();
        addButton.setText("+ " + typeLabel(type) + " جدید");
        reload();
    }

    private void updateTabs() {
        styleTab(hookahTab, "hookah".equals(type), turquoise, softTeal);
        styleTab(drinkTab, "drink".equals(type), Color.rgb(55, 112, 151), Color.rgb(231, 241, 248));
        styleTab(foodTab, "food".equals(type), Color.rgb(163, 102, 43), softGold);
        styleTab(serviceTab, "service".equals(type), brown, Color.rgb(241, 235, 230));
    }

    private void styleTab(Button button, boolean active, int accent, int soft) {
        button.setTextColor(active ? Color.WHITE : accent);
        button.setBackground(rounded(active ? accent : soft, 16));
    }

    private void reload() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();

        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/catalog?type=" + type + "&all=1"
                );
                JSONArray items = response.optJSONArray("items");
                runOnUiThread(() -> render(items));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONArray items) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        TextView sectionInfo = text(
                typeLabel(type) + " • مرتب‌شده بر اساس دسته‌بندی",
                12, muted, false
        );
        sectionInfo.setGravity(Gravity.RIGHT);
        sectionInfo.setPadding(dp(2), 0, dp(2), dp(10));
        content.addView(sectionInfo);

        if (items == null || items.length() == 0) {
            TextView empty = text(
                    "هنوز " + typeLabel(type) + "ی در این بخش تعریف نشده است.",
                    13, muted, false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(34), dp(12), dp(34));
            content.addView(empty);
            return;
        }

        String lastCategory = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String category = item.optString("category_name", "").trim();
            if (category.isEmpty()) category = "بدون دسته‌بندی";

            if (!category.equals(lastCategory)) {
                TextView header = text(category, 14, brown, true);
                header.setGravity(Gravity.RIGHT);
                header.setPadding(dp(2), lastCategory == null ? dp(4) : dp(16), dp(2), dp(8));
                content.addView(header);
                lastCategory = category;
            }

            LinearLayout card = card();

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            row.addView(textBox, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ));

            TextView name = text(item.optString("name", "مورد"), 16, ink, true);
            name.setGravity(Gravity.RIGHT);
            textBox.addView(name);

            TextView price = text(
                    "فروش: " + money(item.optLong("price", 0L)) +
                            "  •  هزینه: " + money(item.optLong("cost", 0L)),
                    11, muted, false
            );
            price.setGravity(Gravity.RIGHT);
            price.setPadding(0, dp(5), 0, 0);
            textBox.addView(price);

            String description = item.optString("description", "").trim();
            if (!description.isEmpty()) {
                TextView desc = text(description, 10, muted, false);
                desc.setGravity(Gravity.RIGHT);
                desc.setPadding(0, dp(6), 0, 0);
                textBox.addView(desc);
            }

            boolean active = item.optInt("active", 1) == 1;
            TextView badge = text(active ? "فعال" : "غیرفعال", 10, active ? green : red, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(10), dp(6), dp(10), dp(6));
            badge.setBackground(rounded(
                    active ? Color.rgb(232,243,235) : Color.rgb(250,235,232), 14
            ));
            row.addView(badge);

            card.addView(row);

            if ("hookah".equals(type) && PermissionStore.has(this, "manage_inventory")) {
                TextView recipeHint = text(
                        "ویرایش مشخصات یا فرمول مصرف مواد اولیه",
                        10, turquoise, true
                );
                recipeHint.setGravity(Gravity.RIGHT);
                recipeHint.setPadding(0, dp(8), 0, 0);
                card.addView(recipeHint);
                card.setOnClickListener(v -> showHookahActions(item));
            } else {
                card.setOnClickListener(v -> prepareEditDialog(item));
            }

            content.addView(card);
        }
    }

    private void showHookahActions(JSONObject item) {
        String[] options = new String[]{"ویرایش مشخصات", "فرمول مصرف مواد اولیه"};
        new AlertDialog.Builder(this)
                .setTitle(item.optString("name", "قلیان"))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        prepareEditDialog(item);
                    } else {
                        Intent intent = new Intent(this, RecipeActivity.class);
                        intent.putExtra("catalog_id", item.optLong("id"));
                        intent.putExtra("catalog_type", "hookah");
                        intent.putExtra("catalog_name", item.optString("name", "قلیان"));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void prepareEditDialog(JSONObject existing) {
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/catalog-categories?section=" + type + "&all=1"
                );
                JSONArray categories = response.optJSONArray("categories");
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    showEditDialog(existing, categories);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void showEditDialog(JSONObject existing, JSONArray categories) {
        boolean editing = existing != null;

        LinearLayout box = dialogBox();

        EditText name = field("نام " + typeLabel(type), false);
        EditText price = field("قیمت فروش (تومان)", true);
        EditText cost = field(
                "hookah".equals(type)
                        ? "هزینه تمام‌شده دستی؛ فرمول می‌تواند خودکار محاسبه کند"
                        : "هزینه تمام‌شده (تومان)",
                true
        );
        EditText description = field("توضیح کوتاه منو؛ اختیاری", false);
        EditText sortOrder = field("اولویت نمایش؛ عدد کمتر بالاتر", true);

        List<Long> categoryIds = new ArrayList<>();
        List<String> categoryNames = new ArrayList<>();
        categoryIds.add(0L);
        categoryNames.add("بدون دسته‌بندی");

        if (categories != null) {
            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.optJSONObject(i);
                if (category == null) continue;
                categoryIds.add(category.optLong("id"));
                String label = category.optString("name", "دسته‌بندی");
                if (category.optInt("active", 1) == 0) label += " • غیرفعال";
                categoryNames.add(label);
            }
        }

        Spinner categorySpinner = new Spinner(this);
        categorySpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                categoryNames
        ));

        CheckBox active = new CheckBox(this);
        active.setText("در منوی فروش فعال باشد");
        active.setTextColor(ink);
        active.setTextSize(13);
        active.setChecked(true);

        if (editing) {
            name.setText(existing.optString("name", ""));
            price.setText(String.valueOf(existing.optLong("price", 0L)));
            cost.setText(String.valueOf(existing.optLong("cost", 0L)));
            description.setText(existing.optString("description", ""));
            sortOrder.setText(String.valueOf(existing.optInt("sort_order", 0)));
            active.setChecked(existing.optInt("active", 1) == 1);

            long selectedCategory = existing.optLong("category_id", 0L);
            for (int i = 0; i < categoryIds.size(); i++) {
                if (categoryIds.get(i) == selectedCategory) {
                    categorySpinner.setSelection(i);
                    break;
                }
            }
        } else {
            sortOrder.setText("0");
            if (categoryIds.size() > 1) categorySpinner.setSelection(1);
        }

        box.addView(name);
        box.addView(price);
        box.addView(cost);
        box.addView(description);

        TextView categoryLabel = text("دسته‌بندی", 11, muted, true);
        categoryLabel.setGravity(Gravity.RIGHT);
        categoryLabel.setPadding(dp(4), dp(5), dp(4), dp(4));
        box.addView(categoryLabel);
        box.addView(categorySpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        box.addView(sortOrder);
        box.addView(active);

        new AlertDialog.Builder(this)
                .setTitle((editing ? "ویرایش " : "") + typeLabel(type))
                .setView(box)
                .setPositiveButton("ذخیره", (dialog, which) -> new Thread(() -> {
                    try {
                        int pos = categorySpinner.getSelectedItemPosition();
                        long categoryId = pos >= 0 && pos < categoryIds.size()
                                ? categoryIds.get(pos) : 0L;

                        JSONObject body = new JSONObject();
                        body.put("name", name.getText().toString().trim());
                        body.put("price", parseLong(price.getText().toString()));
                        body.put("cost", parseLong(cost.getText().toString()));
                        body.put("description", description.getText().toString().trim());
                        body.put("category_id", categoryId);
                        body.put("sort_order", parseLong(sortOrder.getText().toString()));
                        body.put("active", active.isChecked());

                        if (editing) {
                            ApiClient.patch(
                                    this,
                                    "/api/catalog/" + type + "/" + existing.optLong("id"),
                                    body
                            );
                        } else {
                            body.put("type", type);
                            ApiClient.post(this, "/api/catalog", body);
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(this, "آیتم منو ذخیره شد.", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showCategoryManager() {
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/catalog-categories?section=" + type + "&all=1"
                );
                JSONArray categories = response.optJSONArray("categories");
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    renderCategoryManager(categories);
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderCategoryManager(JSONArray categories) {
        LinearLayout box = dialogBox();

        TextView help = text(
                "دسته‌بندی‌های " + typeLabel(type) +
                        " ترتیب و نمایش منوی فروش را کنترل می‌کنند.",
                11, muted, false
        );
        help.setGravity(Gravity.RIGHT);
        help.setPadding(dp(4), 0, dp(4), dp(10));
        box.addView(help);

        Button add = primaryButton("+ دسته‌بندی جدید");
        add.setOnClickListener(v -> showCategoryDialog(null));
        box.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ));

        if (categories == null || categories.length() == 0) {
            TextView empty = text("هنوز دسته‌بندی‌ای ثبت نشده است.", 12, muted, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(22), dp(8), dp(22));
            box.addView(empty);
        } else {
            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.optJSONObject(i);
                if (category == null) continue;

                LinearLayout row = card();
                TextView name = text(category.optString("name", "دسته‌بندی"), 14, ink, true);
                name.setGravity(Gravity.RIGHT);
                row.addView(name);

                String status = category.optInt("active", 1) == 1 ? "فعال" : "غیرفعال";
                TextView meta = text(
                        "اولویت: " + JalaliDateTime.fa(String.valueOf(category.optInt("sort_order", 0))) +
                                " • " + status,
                        10,
                        category.optInt("active", 1) == 1 ? muted : red,
                        false
                );
                meta.setGravity(Gravity.RIGHT);
                meta.setPadding(0, dp(5), 0, 0);
                row.addView(meta);
                row.setOnClickListener(v -> showCategoryDialog(category));
                box.addView(row);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        new AlertDialog.Builder(this)
                .setTitle("دسته‌بندی‌های " + typeLabel(type))
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .show();
    }

    private void showCategoryDialog(JSONObject existing) {
        boolean editing = existing != null;
        LinearLayout box = dialogBox();

        EditText name = field("نام دسته‌بندی", false);
        EditText sort = field("اولویت نمایش", true);
        CheckBox active = new CheckBox(this);
        active.setText("دسته‌بندی فعال باشد");
        active.setTextColor(ink);
        active.setChecked(true);

        if (editing) {
            name.setText(existing.optString("name", ""));
            sort.setText(String.valueOf(existing.optInt("sort_order", 0)));
            active.setChecked(existing.optInt("active", 1) == 1);
        } else {
            sort.setText("0");
        }

        box.addView(name);
        box.addView(sort);
        box.addView(active);

        new AlertDialog.Builder(this)
                .setTitle(editing ? "ویرایش دسته‌بندی" : "دسته‌بندی جدید")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("name", name.getText().toString().trim());
                        body.put("sort_order", parseLong(sort.getText().toString()));
                        body.put("active", active.isChecked());

                        if (editing) {
                            ApiClient.patch(
                                    this,
                                    "/api/catalog-categories/" + existing.optLong("id"),
                                    body
                            );
                        } else {
                            body.put("section", type);
                            ApiClient.post(this, "/api/catalog-categories", body);
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(this, "دسته‌بندی ذخیره شد.", Toast.LENGTH_SHORT).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private String typeLabel(String value) {
        if ("hookah".equals(value)) return "قلیان";
        if ("drink".equals(value)) return "نوشیدنی";
        if ("food".equals(value)) return "خوراکی";
        return "خدمت";
    }

    private Button tabButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return b;
    }

    private Button primaryButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(turquoise, 17));
        return b;
    }

    private Button secondaryButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(12);
        b.setTextColor(brown);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(softGold, 17));
        return b;
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
