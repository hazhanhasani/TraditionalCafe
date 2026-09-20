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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

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
    private Button serviceTab;
    private Button addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (!PermissionStore.has(this, "manage_catalog")) {
            Toast.makeText(this, "مجوز تعریف قلیان و خدمات برای این حساب فعال نیست.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

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

        TextView title = text("تعریف قلیان و خدمات", 21, ink, true);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> reload());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(16), dp(4), dp(16), dp(10));

        hookahTab = tabButton("قلیان‌ها");
        serviceTab = tabButton("خدمات");

        hookahTab.setOnClickListener(v -> switchType("hookah"));
        serviceTab.setOnClickListener(v -> switchType("service"));

        tabs.addView(hookahTab, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams serviceLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        serviceLp.setMarginStart(dp(8));
        tabs.addView(serviceTab, serviceLp);
        root.addView(tabs);

        addButton = primaryButton("+ تعریف قلیان");
        addButton.setOnClickListener(v -> showEditDialog(null));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        addLp.setMargins(dp(16), 0, dp(16), dp(10));
        root.addView(addButton, addLp);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
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
        addButton.setText("hookah".equals(type) ? "+ تعریف قلیان" : "+ تعریف خدمت");
        reload();
    }

    private void updateTabs() {
        boolean hookah = "hookah".equals(type);
        hookahTab.setTextColor(hookah ? Color.WHITE : turquoise);
        hookahTab.setBackground(rounded(hookah ? turquoise : softTeal, 16));
        serviceTab.setTextColor(!hookah ? Color.WHITE : brown);
        serviceTab.setBackground(rounded(!hookah ? brown : softGold, 16));
    }

    private void reload() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();

        new Thread(() -> {
            try {
                JSONObject r = ApiClient.get(this, "/api/catalog?type=" + type + "&all=1");
                JSONArray items = r.optJSONArray("items");
                runOnUiThread(() -> render(items));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render(JSONArray items) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        if (items == null || items.length() == 0) {
            TextView empty = text(
                    "hookah".equals(type)
                            ? "هنوز قلیانی تعریف نشده است."
                            : "هنوز خدمتی تعریف نشده است.",
                    13, muted, false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(34), dp(12), dp(34));
            content.addView(empty);
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            LinearLayout card = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            row.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = text(item.optString("name", "مورد"), 16, ink, true);
            name.setGravity(Gravity.RIGHT);
            textBox.addView(name);

            TextView price = text(
                    "فروش: " + money(item.optLong("price", 0)) +
                    "  •  هزینه: " + money(item.optLong("cost", 0)),
                    11, muted, false
            );
            price.setGravity(Gravity.RIGHT);
            price.setPadding(0, dp(5), 0, 0);
            textBox.addView(price);

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
                        "فرمول مصرف مواد اولیه • برای مدیریت روی کارت بزنید",
                        10, turquoise, true
                );
                recipeHint.setGravity(Gravity.RIGHT);
                recipeHint.setPadding(0, dp(8), 0, 0);
                card.addView(recipeHint);

                card.setOnClickListener(v -> showHookahActions(item));
            } else {
                card.setOnClickListener(v -> showEditDialog(item));
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
                        showEditDialog(item);
                    } else {
                        android.content.Intent intent = new android.content.Intent(
                                this,
                                RecipeActivity.class
                        );
                        intent.putExtra("catalog_id", item.optLong("id"));
                        intent.putExtra("catalog_type", "hookah");
                        intent.putExtra("catalog_name", item.optString("name", "قلیان"));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showEditDialog(JSONObject existing) {
        boolean editing = existing != null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);

        EditText name = field("نام", false);
        EditText price = field("قیمت فروش (تومان)", true);
        EditText cost = field(
                "hookah".equals(type)
                        ? "هزینه تمام‌شده دستی؛ با فرمول خودکار محاسبه می‌شود"
                        : "هزینه تمام‌شده (تومان)",
                true
        );
        CheckBox active = new CheckBox(this);
        active.setText("فعال باشد");
        active.setTextColor(ink);
        active.setTextSize(13);

        if (editing) {
            name.setText(existing.optString("name", ""));
            price.setText(String.valueOf(existing.optLong("price", 0)));
            cost.setText(String.valueOf(existing.optLong("cost", 0)));
            active.setChecked(existing.optInt("active", 1) == 1);
        } else {
            active.setChecked(true);
        }

        box.addView(name);
        box.addView(price);
        box.addView(cost);
        box.addView(active);

        new AlertDialog.Builder(this)
                .setTitle(editing
                        ? ("hookah".equals(type) ? "ویرایش قلیان" : "ویرایش خدمت")
                        : ("hookah".equals(type) ? "قلیان جدید" : "خدمت جدید"))
                .setView(box)
                .setPositiveButton("ذخیره", (dialog, which) -> {
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("name", name.getText().toString().trim());
                            body.put("price", parseLong(price.getText().toString()));
                            body.put("cost", parseLong(cost.getText().toString()));
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
                                Toast.makeText(this, "ذخیره شد.", Toast.LENGTH_SHORT).show();
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
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(turquoise, 17));
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
        return String.format(Locale.US, "%,d تومان", value);
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
