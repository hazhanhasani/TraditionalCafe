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

public class UserManagementActivity extends Activity {

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
    private long currentUserId = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String role = getSharedPreferences("session", MODE_PRIVATE).getString("role", "staff");
        if (!"admin".equals(role)) {
            Toast.makeText(this, "مدیریت کاربران فقط برای مدیر فعال است.", Toast.LENGTH_LONG).show();
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
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text("کاربران و دسترسی‌ها", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text("حساب‌ها، نقش‌ها، رمز عبور و فعالیت", 11, muted, false);
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

        Button add = primaryButton("+ کاربر جدید");
        add.setOnClickListener(v -> showCreateDialog());
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button permissions = secondaryButton("مجوز صندوق‌دار");
        permissions.setOnClickListener(v -> showCashierPermissions());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        pLp.setMarginStart(dp(8));
        actions.addView(permissions, pLp);
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
                JSONObject me = ApiClient.get(this, "/api/me");
                JSONObject meUser = me.optJSONObject("user");
                if (meUser != null) currentUserId = meUser.optLong("id", 0L);

                JSONObject response = ApiClient.get(this, "/api/users");
                JSONArray users = response.optJSONArray("users");
                runOnUiThread(() -> renderUsers(users));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderUsers(JSONArray users) {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        TextView info = text(
                "مدیر دسترسی کامل دارد. شاگرد فقط فروش شخصی و نسیه را مدیریت می‌کند. مجوزهای عملیاتی صندوق‌دار قابل تنظیم است.",
                11, muted, false
        );
        info.setGravity(Gravity.RIGHT);
        info.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(info);

        if (users == null || users.length() == 0) {
            showEmpty("هیچ کاربری ثبت نشده است.");
            return;
        }

        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.optJSONObject(i);
            if (user == null) continue;

            long id = user.optLong("id");
            boolean active = user.optInt("active", 1) == 1;
            String role = user.optString("role", "staff");

            LinearLayout card = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            row.addView(textBox, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ));

            TextView nameView = text(
                    user.optString("name", "کاربر") + (id == currentUserId ? " • حساب فعلی" : ""),
                    16, ink, true
            );
            nameView.setGravity(Gravity.RIGHT);
            textBox.addView(nameView);

            TextView username = text(
                    "@" + user.optString("username", "") + " • " + roleLabel(role),
                    11, muted, false
            );
            username.setGravity(Gravity.RIGHT);
            username.setPadding(0, dp(4), 0, 0);
            textBox.addView(username);

            TextView status = text(active ? "فعال" : "غیرفعال", 10, active ? green : red, true);
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(9), dp(6), dp(9), dp(6));
            status.setBackground(rounded(
                    active ? Color.rgb(232,243,235) : Color.rgb(250,235,232), 14
            ));
            row.addView(status);
            card.addView(row);

            String lastLogin = user.optString("last_login", "");
            String lastActivity = user.optString("last_activity", "");
            boolean openShift = user.optInt("has_open_shift", 0) == 1;

            TextView meta = text(
                    "آخرین ورود: " + (lastLogin.isEmpty() ? "ثبت نشده" : JalaliDateTime.formatUtcCompact(lastLogin)) +
                            "\nآخرین فعالیت: " + (lastActivity.isEmpty() ? "ثبت نشده" : JalaliDateTime.formatUtcCompact(lastActivity)) +
                            (openShift ? "\nشیفت: باز" : ""),
                    10, openShift ? brown : muted, false
            );
            meta.setGravity(Gravity.RIGHT);
            meta.setPadding(0, dp(8), 0, 0);
            card.addView(meta);

            card.setOnClickListener(v -> showUserActions(user));
            content.addView(card);
        }
    }

    private void showUserActions(JSONObject user) {
        String[] options = new String[]{"ویرایش حساب", "تغییر رمز عبور", "مشاهده فعالیت‌ها"};
        new AlertDialog.Builder(this)
                .setTitle(user.optString("name", "کاربر"))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditDialog(user);
                    else if (which == 1) showPasswordDialog(user);
                    else showActivity(user);
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showCreateDialog() {
        LinearLayout box = dialogBox();
        EditText name = field("نام نمایشی", false);
        EditText username = field("نام کاربری انگلیسی", false);
        username.setTextDirection(View.TEXT_DIRECTION_LTR);
        username.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        EditText password = field("رمز عبور؛ حداقل ۸ کاراکتر", true);
        Spinner role = roleSpinner("staff");

        box.addView(name);
        box.addView(username);
        box.addView(password);

        TextView roleTitle = text("نقش", 12, ink, true);
        roleTitle.setGravity(Gravity.RIGHT);
        box.addView(roleTitle);
        box.addView(role, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));

        new AlertDialog.Builder(this)
                .setTitle("کاربر جدید")
                .setView(box)
                .setPositiveButton("ساخت حساب", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("name", name.getText().toString().trim());
                        body.put("username", username.getText().toString().trim().toLowerCase());
                        body.put("password", password.getText().toString());
                        body.put("role", spinnerRole(role));
                        ApiClient.post(this, "/api/users", body);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "حساب کاربر ساخته شد.", Toast.LENGTH_LONG).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showEditDialog(JSONObject user) {
        LinearLayout box = dialogBox();

        EditText name = field("نام نمایشی", false);
        name.setText(user.optString("name", ""));

        EditText username = field("نام کاربری انگلیسی", false);
        username.setText(user.optString("username", ""));
        username.setTextDirection(View.TEXT_DIRECTION_LTR);
        username.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);

        Spinner role = roleSpinner(user.optString("role", "staff"));

        CheckBox active = new CheckBox(this);
        active.setText("حساب فعال باشد");
        active.setTextColor(ink);
        active.setChecked(user.optInt("active", 1) == 1);

        box.addView(name);
        box.addView(username);
        TextView roleTitle = text("نقش", 12, ink, true);
        roleTitle.setGravity(Gravity.RIGHT);
        box.addView(roleTitle);
        box.addView(role, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));
        box.addView(active);

        new AlertDialog.Builder(this)
                .setTitle("ویرایش حساب")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("name", name.getText().toString().trim());
                        body.put("username", username.getText().toString().trim().toLowerCase());
                        body.put("role", spinnerRole(role));
                        body.put("active", active.isChecked());
                        ApiClient.patch(this, "/api/users/" + user.optLong("id"), body);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "اطلاعات کاربر ذخیره شد.", Toast.LENGTH_LONG).show();
                            reload();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showPasswordDialog(JSONObject user) {
        LinearLayout box = dialogBox();
        EditText password = field("رمز جدید؛ حداقل ۸ کاراکتر", true);
        EditText confirm = field("تکرار رمز جدید", true);
        box.addView(password);
        box.addView(confirm);

        new AlertDialog.Builder(this)
                .setTitle("تغییر رمز • " + user.optString("name", "کاربر"))
                .setMessage("بعد از تغییر رمز، نشست‌های قبلی این کاربر بسته می‌شوند.")
                .setView(box)
                .setPositiveButton("تغییر رمز", (d,w) -> {
                    String p1 = password.getText().toString();
                    String p2 = confirm.getText().toString();
                    if (p1.length() < 8 || !p1.equals(p2)) {
                        showError("رمز باید حداقل ۸ کاراکتر باشد و تکرار آن یکسان باشد.");
                        return;
                    }
                    new Thread(() -> {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("password", p1);
                            ApiClient.patch(this, "/api/users/" + user.optLong("id"), body);
                            runOnUiThread(() ->
                                    Toast.makeText(this, "رمز عبور تغییر کرد.", Toast.LENGTH_LONG).show()
                            );
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showActivity(JSONObject user) {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/users/" + user.optLong("id") + "/activity"
                );
                JSONArray activity = response.optJSONArray("activity");
                runOnUiThread(() -> renderActivityDialog(user, activity));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderActivityDialog(JSONObject user, JSONArray activity) {
        StringBuilder message = new StringBuilder();
        int count = activity == null ? 0 : Math.min(activity.length(), 30);
        for (int i = 0; i < count; i++) {
            JSONObject log = activity.optJSONObject(i);
            if (log == null) continue;
            if (message.length() > 0) message.append("\n\n");
            message.append(activityLabel(log.optString("action", "")));
            String created = log.optString("created_at", "");
            if (!created.isEmpty()) {
                message.append("\n").append(JalaliDateTime.formatUtcCompact(created));
            }
        }
        if (message.length() == 0) message.append("فعالیتی ثبت نشده است.");

        ScrollView scroll = new ScrollView(this);
        TextView body = text(message.toString(), 12, ink, false);
        body.setGravity(Gravity.RIGHT);
        body.setPadding(dp(18), dp(12), dp(18), dp(12));
        scroll.addView(body);

        new AlertDialog.Builder(this)
                .setTitle("فعالیت‌های " + user.optString("name", "کاربر"))
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .show();
    }

    private void showCashierPermissions() {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(this, "/api/role-permissions");
                JSONObject roles = response.optJSONObject("roles");
                JSONObject cashier = roles == null ? null : roles.optJSONObject("cashier");
                runOnUiThread(() -> renderPermissionDialog(cashier));
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void renderPermissionDialog(JSONObject permissions) {
        if (permissions == null) permissions = new JSONObject();

        LinearLayout box = dialogBox();
        TextView note = text(
                "مدیر همیشه دسترسی کامل دارد. دسترسی شاگرد برای حفظ محدودیت فروش شخصی و نسیه قفل است.",
                11, muted, false
        );
        note.setGravity(Gravity.RIGHT);
        note.setPadding(dp(4), 0, dp(4), dp(9));
        box.addView(note);

        String[] keys = new String[]{
                "view_all_orders","manage_catalog","manage_expenses","view_reports",
                "reverse_settlement","apply_discount","view_all_shifts","manage_inventory",
                "manage_customer_limits",
                "adjust_customer_ledger",
                "view_audit_log"
        };
        String[] labels = new String[]{
                "مدیریت سفارش‌های همه کاربران",
                "تعریف و ویرایش قلیان و خدمات",
                "مشاهده و ثبت هزینه‌ها",
                "مشاهده گزارش‌ها و سود و زیان",
                "برگرداندن تسویه اشتباه",
                "ثبت تخفیف روی سفارش",
                "مشاهده شیفت همه کاربران",
                "مدیریت انبار و موجودی",
                "تنظیم سقف اعتبار و مهلت پرداخت مشتریان",
                "اصلاح دستی مانده حساب مشتریان",
                "مشاهده مرکز فعالیت‌ها و لاگ مدیریتی"
        };

        List<CheckBox> checks = new ArrayList<>();
        JSONObject finalPermissions = permissions;
        for (int i = 0; i < keys.length; i++) {
            CheckBox check = new CheckBox(this);
            check.setText(labels[i]);
            check.setTextColor(ink);
            check.setTextSize(12);
            check.setChecked(finalPermissions.optBoolean(keys[i], true));
            box.addView(check);
            checks.add(check);
        }

        new AlertDialog.Builder(this)
                .setTitle("مجوزهای صندوق‌دار")
                .setView(box)
                .setPositiveButton("ذخیره", (d,w) -> new Thread(() -> {
                    try {
                        JSONObject values = new JSONObject();
                        for (int i = 0; i < keys.length; i++) {
                            values.put(keys[i], checks.get(i).isChecked());
                        }
                        JSONObject body = new JSONObject();
                        body.put("permissions", values);
                        ApiClient.patch(this, "/api/role-permissions/cashier", body);
                        runOnUiThread(() ->
                                Toast.makeText(this, "مجوزهای صندوق‌دار ذخیره شد.", Toast.LENGTH_LONG).show()
                        );
                    } catch (Exception e) {
                        runOnUiThread(() -> showError(e.getMessage()));
                    }
                }).start())
                .setNegativeButton("لغو", null)
                .show();
    }

    private Spinner roleSpinner(String selectedRole) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"شاگرد", "صندوق‌دار", "مدیر"}
        ));
        spinner.setSelection(
                "admin".equals(selectedRole) ? 2 : "cashier".equals(selectedRole) ? 1 : 0
        );
        return spinner;
    }

    private String spinnerRole(Spinner spinner) {
        return spinner.getSelectedItemPosition() == 2
                ? "admin"
                : spinner.getSelectedItemPosition() == 1 ? "cashier" : "staff";
    }

    private String roleLabel(String role) {
        if ("admin".equals(role)) return "مدیر";
        if ("cashier".equals(role)) return "صندوق‌دار";
        return "شاگرد";
    }

    private String activityLabel(String action) {
        if ("login".equals(action)) return "ورود به حساب";
        if ("open_shift".equals(action)) return "شروع شیفت";
        if ("close_shift".equals(action)) return "پایان شیفت";
        if ("settle_order".equals(action)) return "تسویه سفارش";
        if ("cancel_order".equals(action)) return "لغو سفارش";
        if ("customer_payment".equals(action)) return "ثبت پرداخت بدهی";
        if ("create_expense".equals(action)) return "ثبت هزینه";
        if ("create_user".equals(action)) return "ساخت کاربر";
        if ("update_user".equals(action)) return "ویرایش کاربر";
        if ("update_role_permissions".equals(action)) return "تغییر مجوز نقش";
        return action == null || action.isEmpty() ? "فعالیت" : action;
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

    private EditText field(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(Color.rgb(250,248,244), 14));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setTextDirection(View.TEXT_DIRECTION_LTR);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }
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
