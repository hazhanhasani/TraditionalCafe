package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int brown = Color.rgb(92, 57, 35);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int turquoiseDark = Color.rgb(8, 83, 86);
    private final int gold = Color.rgb(205, 151, 71);
    private final int softGold = Color.rgb(249, 239, 219);
    private final int softTeal = Color.rgb(229, 243, 241);
    private final int green = Color.rgb(62, 135, 95);
    private final int divider = Color.rgb(235, 229, 220);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(surface);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(bg);
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        shell.addView(scroll, scrollLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(buildTopBar());
        content.addView(buildHeroCard());

        content.addView(sectionHeader("نمای کلی امروز", "لحظه‌ای"));
        content.addView(buildMetrics());

        content.addView(sectionHeader("دسترسی سریع", "همه ابزارها"));
        content.addView(buildQuickActions());

        content.addView(sectionHeader("وضعیت میزها", "مدیریت میزها"));
        content.addView(buildTablesCard());

        content.addView(sectionHeader("فعالیت اخیر", "مشاهده همه"));
        content.addView(buildEmptyActivity());

        shell.addView(buildBottomNav());
        return shell;
    }

    private View buildTopBar() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(18));

        LinearLayout avatar = new LinearLayout(this);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(turquoise, 18));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        avatarLp.setMarginEnd(dp(12));
        avatar.setLayoutParams(avatarLp);

        TextView avatarText = label("ک", 20, Color.WHITE, true);
        avatar.addView(avatarText);
        row.addView(avatar);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(titles, titlesLp);

        TextView title = label("کافه سنتی", 22, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = label("داشبورد مدیریت روزانه", 12, muted, false);
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);

        TextView settings = label("⋮", 30, ink, false);
        settings.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(40), dp(46));
        row.addView(settings, settingsLp);

        return row;
    }

    private View buildHeroCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(76, 47, 31), turquoiseDark}
        );
        gradient.setCornerRadius(dp(26));
        card.setBackground(gradient);
        card.setElevation(dp(5));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView today = label("فروش امروز", 14, Color.argb(210, 255, 255, 255), false);
        top.addView(today, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = label("روز کاری", 11, brown, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(6), dp(10), dp(6));
        badge.setBackground(rounded(softGold, 16));
        top.addView(badge);

        card.addView(top);

        TextView amount = label("۰ تومان", 32, Color.WHITE, true);
        amount.setGravity(Gravity.RIGHT);
        amount.setPadding(0, dp(8), 0, dp(12));
        card.addView(amount);

        View line = new View(this);
        line.setBackgroundColor(Color.argb(45, 255, 255, 255));
        card.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(14), 0, 0);

        stats.addView(heroMini("نقدی", "۰"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(heroMini("کارت", "۰"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(heroMini("نسیه", "۰"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(stats);

        return card;
    }

    private View heroMini(String title, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.RIGHT);

        TextView t = label(title, 11, Color.argb(175, 255, 255, 255), false);
        t.setGravity(Gravity.RIGHT);
        box.addView(t);

        TextView v = label(value, 16, Color.WHITE, true);
        v.setGravity(Gravity.RIGHT);
        v.setPadding(0, dp(4), 0, 0);
        box.addView(v);
        return box;
    }

    private View buildMetrics() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        grid.setUseDefaultMargins(false);

        grid.addView(metricCard("قلیان امروز", "۰", "ثبت نشده", R.drawable.ic_hookah, softTeal, turquoise));
        grid.addView(metricCard("طلب دفتری", "۰ تومان", "بدون بدهی", R.drawable.ic_book, softGold, brown));
        grid.addView(metricCard("هزینه امروز", "۰ تومان", "هزینه‌ای ثبت نشده", R.drawable.ic_expense, Color.rgb(250, 235, 232), Color.rgb(170, 76, 62)));
        grid.addView(metricCard("سود امروز", "۰ تومان", "پس از ثبت فروش", R.drawable.ic_chart, Color.rgb(232, 243, 235), green));

        return grid;
    }

    private View metricCard(String titleText, String valueText, String hintText, int iconRes, int iconBg, int iconTint) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface, 20));
        card.setElevation(dp(1));

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(gp);

        LinearLayout iconBox = iconCircle(iconRes, iconBg, iconTint, 38);
        card.addView(iconBox);

        TextView title = label(titleText, 12, muted, false);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(0, dp(10), 0, 0);
        card.addView(title);

        TextView value = label(valueText, 18, ink, true);
        value.setGravity(Gravity.RIGHT);
        value.setPadding(0, dp(4), 0, 0);
        card.addView(value);

        TextView hint = label(hintText, 10, muted, false);
        hint.setGravity(Gravity.RIGHT);
        hint.setPadding(0, dp(5), 0, 0);
        card.addView(hint);

        return card;
    }

    private View buildQuickActions() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setAlignmentMode(GridLayout.ALIGN_MARGINS);

        grid.addView(actionTile("میزها", "سفارش و وضعیت میز", R.drawable.ic_table, turquoise, softTeal));
        grid.addView(actionTile("ثبت قلیان", "ثبت سریع سفارش", R.drawable.ic_hookah, brown, softGold));
        grid.addView(actionTile("حساب دفتری", "بدهی و پرداخت مشتری", R.drawable.ic_book, Color.rgb(92, 78, 148), Color.rgb(239, 236, 249)));
        grid.addView(actionTile("ثبت هزینه", "خرید و هزینه‌های روز", R.drawable.ic_expense, Color.rgb(177, 84, 68), Color.rgb(250, 235, 232)));
        grid.addView(actionTile("تسویه", "نقد، کارت و ترکیبی", R.drawable.ic_wallet, Color.rgb(44, 117, 78), Color.rgb(232, 243, 235)));
        grid.addView(actionTile("گزارش‌ها", "سود و زیان و عملکرد", R.drawable.ic_chart, Color.rgb(174, 124, 45), Color.rgb(251, 241, 220)));

        return grid;
    }

    private View actionTile(String titleText, String subtitleText, int iconRes, int iconTint, int iconBg) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(14), dp(13), dp(14));
        card.setBackground(rounded(surface, 20));
        card.setElevation(dp(1));
        card.setOnClickListener(v -> Toast.makeText(this, titleText, Toast.LENGTH_SHORT).show());

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(gp);

        card.addView(iconCircle(iconRes, iconBg, iconTint, 42));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        texts.setGravity(Gravity.RIGHT);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = label(titleText, 14, ink, true);
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView sub = label(subtitleText, 10, muted, false);
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        return card;
    }

    private View buildTablesCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(surface, 22));
        card.setElevation(dp(1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        header.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = label("۱۲ میز آماده", 15, ink, true);
        t.setGravity(Gravity.RIGHT);
        right.addView(t);

        TextView s = label("فعلاً هیچ میز بازی وجود ندارد", 11, muted, false);
        s.setGravity(Gravity.RIGHT);
        s.setPadding(0, dp(3), 0, 0);
        right.addView(s);

        TextView status = label("همه آزاد", 11, green, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        status.setBackground(rounded(Color.rgb(232, 243, 235), 16));
        header.addView(status);
        card.addView(header);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(0, dp(16), 0, 0);

        for (int i = 1; i <= 6; i++) {
            TextView chip = label(String.valueOf(i), 13, muted, true);
            chip.setGravity(Gravity.CENTER);
            GradientDrawable bgChip = rounded(bg, 14);
            bgChip.setStroke(dp(1), divider);
            chip.setBackground(bgChip);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            cp.setMargins(dp(3), 0, dp(3), 0);
            chips.addView(chip, cp);
        }
        card.addView(chips);

        TextView open = label("+ باز کردن میز جدید", 13, turquoise, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable openBg = rounded(softTeal, 16);
        openBg.setStroke(dp(1), Color.rgb(196, 225, 222));
        open.setBackground(openBg);
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        openLp.topMargin = dp(14);
        card.addView(open, openLp);

        return card;
    }

    private View buildEmptyActivity() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(surface, 20));
        card.setElevation(dp(1));

        card.addView(iconCircle(R.drawable.ic_wallet, softGold, brown, 42));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = label("هنوز تراکنشی ثبت نشده", 13, ink, true);
        title.setGravity(Gravity.RIGHT);
        texts.addView(title);

        TextView sub = label("با ثبت اولین سفارش، فعالیت‌های امروز اینجا نمایش داده می‌شوند.", 10, muted, false);
        sub.setGravity(Gravity.RIGHT);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        return card;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackgroundColor(surface);
        nav.setElevation(dp(12));

        nav.addView(navItem("خانه", R.drawable.ic_home, true), new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navItem("میزها", R.drawable.ic_table, false), new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navItem("دفتر", R.drawable.ic_book, false), new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navItem("گزارش", R.drawable.ic_chart, false), new LinearLayout.LayoutParams(0, dp(58), 1f));

        return nav;
    }

    private View navItem(String title, int iconRes, boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        if (active) item.setBackground(rounded(softTeal, 18));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(active ? turquoise : muted);
        item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView text = label(title, 10, active ? turquoise : muted, active);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(4), 0, 0);
        item.addView(text);

        item.setOnClickListener(v -> Toast.makeText(this, title, Toast.LENGTH_SHORT).show());
        return item;
    }

    private View sectionHeader(String title, String action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(22), dp(2), dp(8));

        TextView titleView = label(title, 17, ink, true);
        titleView.setGravity(Gravity.RIGHT);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView actionView = label(action, 11, turquoise, true);
        actionView.setGravity(Gravity.CENTER);
        row.addView(actionView);

        return row;
    }

    private LinearLayout iconCircle(int iconRes, int bgColor, int tint, int size) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setBackground(rounded(bgColor, size / 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        wrap.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        wrap.addView(icon, new LinearLayout.LayoutParams(dp(size / 2), dp(size / 2)));
        return wrap;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
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
