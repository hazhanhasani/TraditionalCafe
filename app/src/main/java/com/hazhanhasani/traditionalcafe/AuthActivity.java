package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AuthActivity extends Activity {

    private static final String API_BASE = "https://traditionalcafe.hazhanhasani4268-0f9.workers.dev";

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int softTeal = Color.rgb(229, 243, 241);

    private EditText usernameInput;
    private EditText passwordInput;
    private Button primaryButton;
    private TextView modeButton;
    private TextView serverStatusView;
    private boolean setupMode = false;

    private EditText displayNameInput;
    private EditText setupKeyInput;
    private Spinner roleSpinner;
    private LinearLayout setupFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String token = getSharedPreferences("session", MODE_PRIVATE).getString("token", "");
        if (token != null && !token.isEmpty()) {
            openApp();
            return;
        }

        setContentView(buildUi());
        detectSetupState();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView mark = text("ک", 30, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(turquoise, 26));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        markLp.gravity = Gravity.CENTER_HORIZONTAL;
        mark.setLayoutParams(markLp);
        root.addView(mark);

        TextView title = text("کافه سنتی", 27, ink, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, 0);
        root.addView(title);

        TextView subtitle = text("ورود امن به سیستم مدیریت", 13, muted, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(12));
        root.addView(subtitle);

        serverStatusView = text("در حال بررسی اتصال به سرور…", 11, muted, true);
        serverStatusView.setGravity(Gravity.CENTER);
        serverStatusView.setPadding(dp(10), dp(8), dp(10), dp(8));
        serverStatusView.setBackground(rounded(Color.rgb(241, 238, 232), 14));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusLp.gravity = Gravity.CENTER_HORIZONTAL;
        statusLp.bottomMargin = dp(16);
        root.addView(serverStatusView, statusLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(rounded(surface, 24));
        card.setElevation(dp(3));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        usernameInput = field("نام کاربری", false);
        passwordInput = field("رمز عبور", true);
        card.addView(usernameInput);
        card.addView(passwordInput);

        setupFields = new LinearLayout(this);
        setupFields.setOrientation(LinearLayout.VERTICAL);
        setupFields.setVisibility(View.GONE);

        displayNameInput = field("نام نمایشی", false);
        setupKeyInput = field("کلید راه‌اندازی CAFE_SETUP_KEY", true);
        setupKeyInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        setupKeyInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        setupFields.addView(displayNameInput);
        setupFields.addView(setupKeyInput);

        TextView roleLabel = text("نقش کاربر", 12, muted, true);
        roleLabel.setGravity(Gravity.RIGHT);
        roleLabel.setPadding(dp(4), dp(8), dp(4), dp(6));
        setupFields.addView(roleLabel);

        roleSpinner = new Spinner(this);
        ArrayAdapter<String> roles = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"شاگرد", "صندوق‌دار", "مدیر"}
        );
        roleSpinner.setAdapter(roles);
        roleSpinner.setSelection(0);
        roleSpinner.setBackground(rounded(softTeal, 16));
        setupFields.addView(roleSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        card.addView(setupFields);

        primaryButton = new Button(this);
        primaryButton.setText("ورود");
        primaryButton.setTextSize(15);
        primaryButton.setTextColor(Color.WHITE);
        primaryButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        primaryButton.setAllCaps(false);
        primaryButton.setBackground(rounded(turquoise, 18));
        primaryButton.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        buttonLp.topMargin = dp(16);
        card.addView(primaryButton, buttonLp);

        modeButton = text("راه‌اندازی کاربر اولیه", 13, turquoise, true);
        modeButton.setGravity(Gravity.CENTER);
        modeButton.setPadding(0, dp(18), 0, dp(4));
        modeButton.setOnClickListener(v -> toggleMode());
        card.addView(modeButton);

        TextView secure = text(
                "رمز عبور به‌صورت متن ساده ذخیره نمی‌شود و فقط هش آن در سرور نگهداری می‌شود.",
                10, muted, false
        );
        secure.setGravity(Gravity.CENTER);
        secure.setPadding(dp(12), dp(18), dp(12), 0);
        root.addView(secure);

        return scroll;
    }

    private EditText field(String hint, boolean password) {
        LabeledEditText input = new LabeledEditText(this);
        input.setFieldLabel(hint);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(Color.rgb(250, 248, 244), 16));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setTextDirection(View.TEXT_DIRECTION_LTR);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            if ("نام کاربری".equals(hint)) {
                input.setTextDirection(View.TEXT_DIRECTION_LTR);
                input.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            } else {
                input.setTextDirection(View.TEXT_DIRECTION_RTL);
                input.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            }
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        );
        lp.bottomMargin = dp(10);
        input.setLayoutParams(lp);
        return input;
    }

    private void toggleMode() {
        setupMode = !setupMode;
        setupFields.setVisibility(setupMode ? View.VISIBLE : View.GONE);
        primaryButton.setText(setupMode ? "ساخت کاربر و ورود" : "ورود");
        modeButton.setText(setupMode ? "بازگشت به ورود" : "راه‌اندازی کاربر اولیه");
    }

    private void submit() {
        String username = usernameInput.getText().toString().trim().toLowerCase();
        String password = passwordInput.getText().toString();

        if (username.length() < 3 || password.length() < 8) {
            Toast.makeText(this, "نام کاربری یا رمز عبور معتبر نیست.", Toast.LENGTH_LONG).show();
            return;
        }

        primaryButton.setEnabled(false);
        primaryButton.setText("در حال اتصال...");

        if (setupMode) {
            String displayName = displayNameInput.getText().toString().trim();
            String setupKey = setupKeyInput.getText().toString();
            if (displayName.isEmpty()) displayName = username;
            if (setupKey.isEmpty()) {
                resetButton();
                Toast.makeText(this, "کلید راه‌اندازی را وارد کنید.", Toast.LENGTH_LONG).show();
                return;
            }
            String role = roleSpinner.getSelectedItemPosition() == 2
                    ? "admin"
                    : roleSpinner.getSelectedItemPosition() == 1 ? "cashier" : "staff";
            setupUser(username, displayName, password, setupKey, role);
        } else {
            login(username, password);
        }
    }

    private void detectSetupState() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(API_BASE + "/api/setup/status").openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();

                JSONObject response = new JSONObject(body.toString());
                boolean configured = response.optBoolean("configured", false);

                runOnUiThread(() -> {
                    serverStatusView.setText(configured
                            ? "سرور و دیتابیس آماده • سیستم راه‌اندازی شده"
                            : "سرور و دیتابیس آماده • حساب اولیه ساخته نشده");
                    serverStatusView.setTextColor(configured
                            ? Color.rgb(62, 135, 95)
                            : turquoise);
                    serverStatusView.setBackground(rounded(
                            configured ? Color.rgb(232, 243, 235) : softTeal,
                            14
                    ));

                    if (!configured) {
                        if (!setupMode) toggleMode();
                        modeButton.setText("اولین حساب هنوز ساخته نشده است");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    serverStatusView.setText("ارتباط با سرور برقرار نشد • برای تلاش دوباره لمس کنید");
                    serverStatusView.setTextColor(Color.rgb(177, 84, 68));
                    serverStatusView.setBackground(rounded(Color.rgb(250, 235, 232), 14));
                    serverStatusView.setOnClickListener(v -> {
                        serverStatusView.setText("در حال بررسی اتصال به سرور…");
                        serverStatusView.setTextColor(muted);
                        detectSetupState();
                    });
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String errorMessage(Exception error, String fallback) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty() || "Request failed".equals(message)) {
            return fallback;
        }
        return message;
    }

    private void login(String username, String password) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                JSONObject response = post("/api/auth/login", body, null);
                handleAuthResponse(response);
            } catch (Exception e) {
                showError(errorMessage(e, "ورود انجام نشد. اتصال اینترنت یا اطلاعات ورود را بررسی کنید."));
            }
        }).start();
    }

    private void setupUser(String username, String displayName, String password, String setupKey, String role) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("name", displayName);
                body.put("password", password);
                body.put("role", role);
                JSONObject response = post("/api/setup/user", body, setupKey);
                handleAuthResponse(response);
            } catch (Exception e) {
                showError(errorMessage(e, "ساخت کاربر انجام نشد. کلید راه‌اندازی و اتصال اینترنت را بررسی کنید."));
            }
        }).start();
    }

    private JSONObject post(String path, JSONObject body, String setupKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        if (setupKey != null) connection.setRequestProperty("X-Setup-Key", setupKey);
        connection.setDoOutput(true);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }

        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream()
        ));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) text.append(line);
        reader.close();
        connection.disconnect();

        JSONObject response = new JSONObject(text.toString());
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(response.optString("message", "Request failed"));
        }
        return response;
    }

    private void handleAuthResponse(JSONObject response) {
        String token = response.optString("token", "");
        JSONObject user = response.optJSONObject("user");
        if (token.isEmpty() || user == null) {
            showError("پاسخ سرور معتبر نبود.");
            return;
        }

        getSharedPreferences("session", MODE_PRIVATE)
                .edit()
                .putString("token", token)
                .putString("username", user.optString("username", ""))
                .putString("name", user.optString("name", ""))
                .putString("role", user.optString("role", "staff"))
                .apply();

        JSONObject permissions = response.optJSONObject("permissions");
        if (permissions != null) {
            PermissionStore.save(this, permissions);
        }

        runOnUiThread(this::openApp);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            resetButton();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void resetButton() {
        primaryButton.setEnabled(true);
        primaryButton.setText(setupMode ? "ساخت کاربر و ورود" : "ورود");
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
