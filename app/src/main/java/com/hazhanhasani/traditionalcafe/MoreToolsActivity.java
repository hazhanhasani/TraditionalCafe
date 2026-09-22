package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MoreToolsActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int brown = Color.rgb(92, 57, 35);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int softTeal = Color.rgb(229, 243, 241);
    private final int softGold = Color.rgb(249, 239, 219);
    private final int green = Color.rgb(62, 135, 95);

    private String role;
    private boolean isOwner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        role = getSharedPreferences("session", MODE_PRIVATE)
                .getString("role", "staff");
        isOwner = getSharedPreferences("session", MODE_PRIVATE)
                .getInt("is_owner", 0) == 1;

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
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
        top.addView(
                titles,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView title = text("بیشتر", 22, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(
                roleLabel() + " • ابزارهایی که هر روز لازم نیستند",
                11,
                muted,
                false
        );
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(30));
        scroll.addView(content);

        content.addView(buildGuideCard());

        addSection(content, "کارهای روزانه");

        if ("staff".equals(role)) {
            addTool(
                    content,
                    "فروش‌های امروز من",
                    "فقط فروش‌هایی که خودم ثبت کرده‌ام",
                    () -> openOperations("my_sales")
            );
            addTool(
                    content,
                    "شیفت من",
                    "شروع یا پایان شیفت و جمع فروش خودم",
                    () -> startActivity(new Intent(this, ShiftActivity.class))
            );
        } else {
            if (PermissionStore.has(this, "view_all_orders")) {
                addTool(
                        content,
                        "سفارش‌ها",
                        "مشاهده و مدیریت سفارش‌های باز و قبلی",
                        () -> openOperations("orders")
                );
            }
            if (PermissionStore.has(this, "manage_expenses")) {
                addTool(
                        content,
                        "هزینه‌ها",
                        "ثبت هزینه‌های روزانه کافه",
                        () -> openOperations("expenses")
                );
            }
            if (PermissionStore.has(this, "view_reports")) {
                addTool(
                        content,
                        "گزارش روزانه",
                        "فروش، هزینه و عملکرد روز",
                        () -> startActivity(new Intent(this, DailyReportActivity.class))
                );
            }
            addTool(
                    content,
                    "شیفت و صندوق",
                    "شروع، پایان و کنترل شیفت‌ها",
                    () -> startActivity(new Intent(this, ShiftActivity.class))
            );
        }

        if (!"staff".equals(role)) {
            addSection(content, "مدیریت");

            if (PermissionStore.has(this, "manage_catalog")) {
                addTool(
                        content,
                        "منو و قیمت‌ها",
                        "قلیان، نوشیدنی، غذا و خدمات",
                        () -> startActivity(new Intent(this, CatalogActivity.class))
                );
            }

            if (PermissionStore.has(this, "manage_inventory")) {
                addTool(
                        content,
                        "انبار و موجودی",
                        "موجودی، خرید و مواد مصرفی",
                        () -> startActivity(new Intent(this, InventoryActivity.class))
                );
            }

            if (PermissionStore.has(this, "view_audit_log")) {
                addTool(
                        content,
                        "فعالیت کاربران",
                        "مشاهده تغییرات و عملیات مهم",
                        () -> startActivity(new Intent(this, AuditLogActivity.class))
                );
            }
        }

        if ("admin".equals(role)) {
            addSection(
                    content,
                    isOwner ? "تنظیمات مدیر اصلی" : "تنظیمات مدیر"
            );

            if (isOwner) {
                addTool(
                        content,
                        "کاربران",
                        "تعیین مدیر، صندوق‌دار، شاگرد و مدیر اصلی",
                        () -> startActivity(new Intent(this, UserManagementActivity.class))
                );
            }
            addTool(
                    content,
                    "تنظیمات رسید",
                    "نام و مشخصات چاپ روی رسید",
                    () -> startActivity(new Intent(this, ReceiptSettingsActivity.class))
            );
            addTool(
                    content,
                    "پشتیبان و بازیابی",
                    "بکاپ و بازیابی اطلاعات",
                    () -> startActivity(new Intent(this, BackupRecoveryActivity.class))
            );
            addTool(
                    content,
                    "عیب‌یابی /debug",
                    "بررسی کامل سلامت اپ و سرور",
                    () -> startActivity(new Intent(this, DebugActivity.class))
            );
        }

        addSection(content, "سیستم");
        addTool(
                content,
                "حالت آفلاین",
                "وضعیت همگام‌سازی و اطلاعات ذخیره‌شده",
                () -> startActivity(new Intent(this, OfflineCenterActivity.class))
        );

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );
        return root;
    }

    private View buildGuideCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(softTeal, 20));

        TextView title = text("راهنمای خیلی کوتاه", 15, turquoise, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        String guide;
        if ("staff".equals(role)) {
            guide = "برای فروش فقط این مسیر را برو: فروش جدید ← انتخاب میز ← افزودن آیتم ← تسویه.";
        } else {
            guide = "برای کار روزانه از صفحه اصلی استفاده کن. تنظیمات و گزارش‌های تخصصی فقط در همین صفحه «بیشتر» هستند.";
        }

        TextView body = text(guide, 12, ink, false);
        body.setGravity(Gravity.RIGHT);
        body.setPadding(0, dp(7), 0, 0);
        card.addView(body);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void addSection(LinearLayout parent, String value) {
        TextView title = text(value, 16, ink, true);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(dp(2), dp(20), dp(2), dp(8));
        parent.addView(title);
    }

    private void addTool(
            LinearLayout parent,
            String titleValue,
            String description,
            Runnable action
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(surface, 18));
        card.setElevation(dp(1));
        card.setOnClickListener(v -> action.run());

        TextView arrow = text("‹", 26, muted, false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(42)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.RIGHT);
        card.addView(
                texts,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView title = text(titleValue, 14, ink, true);
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView sub = text(description, 10, muted, false);
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        parent.addView(card);
    }

    private void openOperations(String module) {
        Intent intent = new Intent(this, OperationsActivity.class);
        intent.putExtra("module", module);
        startActivity(intent);
    }

    private String roleLabel() {
        if (isOwner) return "مدیر اصلی";
        if ("admin".equals(role)) return "مدیر";
        if ("cashier".equals(role)) return "صندوق‌دار";
        return "شاگرد";
    }

    private TextView text(
            String value,
            int sp,
            int color,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setTypeface(
                Typeface.create(
                        "sans-serif",
                        bold ? Typeface.BOLD : Typeface.NORMAL
                )
        );
        return text;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (
                value *
                getResources().getDisplayMetrics().density
        );
    }
}
