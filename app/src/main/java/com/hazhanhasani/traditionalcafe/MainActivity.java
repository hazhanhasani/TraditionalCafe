package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final int cream = Color.rgb(250, 246, 238);
    private final int brown = Color.rgb(78, 45, 24);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int card = Color.WHITE;
    private final int muted = Color.rgb(110, 101, 94);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(cream);
        getWindow().setNavigationBarColor(cream);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildDashboard());
    }

    private View buildDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cream);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("کافه سنتی");
        title.setTextSize(28f);
        title.setTextColor(brown);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("مدیریت امروز");
        subtitle.setTextSize(14f);
        subtitle.setTextColor(muted);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        GridLayout summary = new GridLayout(this);
        summary.setColumnCount(2);
        summary.setRowCount(2);
        summary.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        summary.setUseDefaultMargins(true);

        summary.addView(statCard("فروش امروز", "۰ تومان"));
        summary.addView(statCard("قلیان امروز", "۰"));
        summary.addView(statCard("طلب دفتری", "۰ تومان"));
        summary.addView(statCard("صندوق فعلی", "۰ تومان"));
        root.addView(summary);

        root.addView(sectionTitle("دسترسی سریع"));
        root.addView(actionCard("میزها", "ثبت و مدیریت سفارش هر میز"));
        root.addView(actionCard("ثبت قلیان", "ثبت سریع قلیان و طعم"));
        root.addView(actionCard("حساب دفتری", "مشتریان بدهکار و پرداخت‌ها"));
        root.addView(actionCard("هزینه‌ها", "ثبت هزینه روزانه"));
        root.addView(actionCard("تسویه", "نقدی، کارت، کارت‌به‌کارت و ترکیبی"));
        root.addView(actionCard("گزارش‌ها", "سود و زیان و عملکرد روزانه"));

        TextView footer = new TextView(this);
        footer.setText("نسخه پایه • آماده اتصال به Cloudflare Worker");
        footer.setTextSize(12f);
        footer.setTextColor(muted);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        root.addView(footer);

        scroll.addView(root);
        return scroll;
    }

    private View statCard(String titleText, String valueText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(rounded(card, 18));
        box.setElevation(dp(2));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        box.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(13f);
        title.setTextColor(muted);
        title.setGravity(Gravity.RIGHT);
        box.addView(title);

        TextView value = new TextView(this);
        value.setText(valueText);
        value.setTextSize(18f);
        value.setTextColor(brown);
        value.setTypeface(value.getTypeface(), Typeface.BOLD);
        value.setGravity(Gravity.RIGHT);
        value.setPadding(0, dp(8), 0, 0);
        box.addView(value);

        return box;
    }

    private View actionCard(String titleText, String subtitleText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(card, 16));
        box.setElevation(dp(1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        box.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(17f);
        title.setTextColor(turquoise);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(13f);
        subtitle.setTextColor(muted);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(5), 0, 0);
        box.addView(subtitle);

        return box;
    }

    private View sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18f);
        view.setTextColor(brown);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(0, dp(24), 0, dp(4));
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
