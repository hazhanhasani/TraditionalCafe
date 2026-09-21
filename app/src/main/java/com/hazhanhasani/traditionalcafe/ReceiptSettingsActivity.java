package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class ReceiptSettingsActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);

    private ProgressBar loading;
    private EditText businessName;
    private EditText phone;
    private EditText address;
    private EditText footer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String role = getSharedPreferences("session", MODE_PRIVATE)
                .getString("role", "staff");
        if (!"admin".equals(role)) {
            Toast.makeText(
                    this,
                    "تنظیمات رسید فقط برای مدیر قابل دسترسی است.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        loadSettings();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

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

        TextView title = text("تنظیمات رسید", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(
                "اطلاعاتی که روی نسخه مشتری چاپ و اشتراک‌گذاری می‌شود",
                11, muted, false
        );
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadingLp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(30));

        TextView warning = text(
                "این بخش فقط مشخصات عمومی رسید را تغییر می‌دهد و هیچ اطلاعات سود، هزینه یا حسابداری داخلی روی رسید مشتری نمایش داده نمی‌شود.",
                11,
                muted,
                false
        );
        warning.setGravity(Gravity.RIGHT);
        warning.setPadding(dp(4), dp(4), dp(4), dp(14));
        content.addView(warning);

        businessName = field("نام مجموعه");
        phone = field("شماره تماس");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        address = field("آدرس");
        footer = field("متن انتهای رسید");

        content.addView(businessName);
        content.addView(phone);
        content.addView(address);
        content.addView(footer);

        Button save = new Button(this);
        save.setText("ذخیره تنظیمات رسید");
        save.setTextSize(13);
        save.setTextColor(Color.WHITE);
        save.setAllCaps(false);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setBackground(rounded(turquoise, 16));
        save.setOnClickListener(v -> saveSettings());

        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        saveLp.topMargin = dp(12);
        content.addView(save, saveLp);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        return root;
    }

    private void loadSettings() {
        loading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(this, "/api/receipt-settings");
                JSONObject settings = response.optJSONObject("settings");
                if (settings == null) settings = new JSONObject();

                JSONObject finalSettings = settings;
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    businessName.setText(finalSettings.optString("business_name", "کافه سنتی"));
                    phone.setText(finalSettings.optString("phone", ""));
                    address.setText(finalSettings.optString("address", ""));
                    footer.setText(finalSettings.optString(
                            "footer",
                            "از همراهی شما سپاسگزاریم."
                    ));
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void saveSettings() {
        String nameValue = businessName.getText().toString().trim();
        if (nameValue.length() < 2) {
            showError("نام مجموعه را وارد کنید.");
            return;
        }

        loading.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("business_name", nameValue);
                body.put("phone", phone.getText().toString().trim());
                body.put("address", address.getText().toString().trim());
                body.put("footer", footer.getText().toString().trim());

                ApiClient.patch(this, "/api/receipt-settings", body);

                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(
                            this,
                            "تنظیمات رسید ذخیره شد.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private TextView label(String value) {
        TextView label = text(value, 12, ink, true);
        label.setGravity(Gravity.RIGHT);
        label.setPadding(dp(4), dp(7), dp(4), dp(5));
        return label;
    }

    private EditText field(String hint) {
        LabeledEditText input = new LabeledEditText(this);
        input.setFieldLabel(hint);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(Color.WHITE, 14));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        );
        lp.bottomMargin = dp(7);
        input.setLayoutParams(lp);
        return input;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create(
                "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL
        ));
        return text;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(
                this,
                message == null || message.trim().isEmpty()
                        ? "خطا در تنظیمات رسید."
                        : message,
                Toast.LENGTH_LONG
        ).show();
    }

    private int dp(int value) {
        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
