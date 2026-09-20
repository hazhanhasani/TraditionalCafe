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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class AuditLogActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int red = Color.rgb(177, 84, 68);
    private final int softGold = Color.rgb(249, 239, 219);

    private static final DateTimeFormatter KEY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

    private LinearLayout content;
    private ProgressBar loading;
    private EditText search;
    private Spinner userFilter;
    private Spinner categoryFilter;
    private TextView dateFilterView;

    private final List<JSONObject> logs = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<String> userLabels = new ArrayList<>();

    private Long selectedUserId = null;
    private String selectedCategory = "";
    private LocalDate selectedDate;
    private boolean allDates = false;
    private Long nextBeforeId = null;
    private JSONObject summary = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PermissionStore.has(this, "view_audit_log")) {
            Toast.makeText(
                    this,
                    "مجوز مشاهده مرکز فعالیت‌ها برای این حساب فعال نیست.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        selectedDate = JalaliDateTime.iranNow().toLocalDate();

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        load(false);
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

        TextView title = text("مرکز فعالیت‌ها", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(
                "ثبت تغییرات، عملیات حساس و فعالیت کاربران",
                11,
                muted,
                false
        );
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> load(false));
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.VERTICAL);
        filters.setPadding(dp(16), dp(2), dp(16), dp(10));

        search = field("جستجو در کاربر، نوع فعالیت یا جزئیات");
        filters.addView(search);

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);

        Button today = secondaryButton("امروز");
        today.setOnClickListener(v -> {
            allDates = false;
            selectedDate = JalaliDateTime.iranNow().toLocalDate();
            updateDateText();
            load(false);
        });
        dateRow.addView(today, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button all = secondaryButton("همه تاریخ‌ها");
        all.setOnClickListener(v -> {
            allDates = true;
            updateDateText();
            load(false);
        });
        LinearLayout.LayoutParams allLp =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
        allLp.setMarginStart(dp(8));
        dateRow.addView(all, allLp);

        Button custom = secondaryButton("تاریخ شمسی");
        custom.setOnClickListener(v -> showJalaliDateDialog());
        LinearLayout.LayoutParams customLp =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
        customLp.setMarginStart(dp(8));
        dateRow.addView(custom, customLp);

        filters.addView(dateRow);

        dateFilterView = text("", 11, brown, true);
        dateFilterView.setGravity(Gravity.RIGHT);
        dateFilterView.setPadding(dp(4), dp(7), dp(4), dp(7));
        filters.addView(dateFilterView);
        updateDateText();

        categoryFilter = new Spinner(this);
        categoryFilter.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "همه دسته‌ها",
                        "فروش و سفارش",
                        "مالی و صندوق",
                        "انبار",
                        "منو",
                        "مشتریان",
                        "امنیت و کاربران",
                        "سیستم"
                }
        ));
        filters.addView(categoryFilter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        userFilter = new Spinner(this);
        userLabels.add("همه کاربران");
        userIds.add(0L);
        userFilter.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                userLabels
        ));
        filters.addView(userFilter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        Button apply = primaryButton("اعمال فیلتر");
        apply.setOnClickListener(v -> {
            selectedCategory = categoryKey(
                    categoryFilter.getSelectedItemPosition()
            );
            int pos = userFilter.getSelectedItemPosition();
            selectedUserId = pos > 0 && pos < userIds.size()
                    ? userIds.get(pos)
                    : null;
            load(false);
        });
        filters.addView(apply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        root.addView(filters);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadLp =
                new LinearLayout.LayoutParams(dp(34), dp(34));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(28));
        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
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

    private void load(boolean more) {
        if (loading == null) return;
        loading.setVisibility(View.VISIBLE);

        if (!more) {
            logs.clear();
            nextBeforeId = null;
            content.removeAllViews();
        }

        new Thread(() -> {
            try {
                StringBuilder path =
                        new StringBuilder("/api/audit?limit=60");

                if (!allDates && selectedDate != null) {
                    path.append("&date=")
                            .append(selectedDate.format(KEY));
                }

                if (selectedUserId != null && selectedUserId > 0) {
                    path.append("&user_id=").append(selectedUserId);
                }

                if (selectedCategory != null &&
                        !selectedCategory.isEmpty()) {
                    path.append("&category=")
                            .append(selectedCategory);
                }

                String q = search == null
                        ? ""
                        : search.getText().toString().trim();
                if (!q.isEmpty()) {
                    path.append("&q=")
                            .append(
                                    URLEncoder.encode(
                                            q,
                                            "UTF-8"
                                    )
                            );
                }

                if (more && nextBeforeId != null) {
                    path.append("&before_id=")
                            .append(nextBeforeId);
                }

                JSONObject response =
                        ApiClient.get(this, path.toString());

                JSONArray loaded =
                        response.optJSONArray("logs");
                if (loaded != null) {
                    for (int i = 0; i < loaded.length(); i++) {
                        JSONObject row =
                                loaded.optJSONObject(i);
                        if (row != null) {
                            logs.add(
                                    new JSONObject(
                                            row.toString()
                                    )
                            );
                        }
                    }
                }

                summary =
                        response.optJSONObject("summary");
                if (summary == null) {
                    summary = new JSONObject();
                }

                Object next =
                        response.opt("next_before_id");
                nextBeforeId =
                        next == null ||
                        next == JSONObject.NULL
                                ? null
                                : response.optLong(
                                        "next_before_id"
                                );

                JSONArray users =
                        response.optJSONArray("users");

                runOnUiThread(() -> {
                    if (!more) {
                        populateUsers(users);
                    }
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(
                        () -> showError(e.getMessage())
                );
            }
        }).start();
    }

    private void populateUsers(JSONArray users) {
        long keepId =
                selectedUserId == null
                        ? 0L
                        : selectedUserId;

        userIds.clear();
        userLabels.clear();
        userIds.add(0L);
        userLabels.add("همه کاربران");

        if (users != null) {
            for (int i = 0; i < users.length(); i++) {
                JSONObject user =
                        users.optJSONObject(i);
                if (user == null) continue;

                userIds.add(user.optLong("id"));
                userLabels.add(
                        user.optString("name", "کاربر") +
                                " • " +
                                roleLabel(
                                        user.optString(
                                                "role",
                                                ""
                                        )
                                )
                );
            }
        }

        userFilter.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_dropdown_item,
                        userLabels
                )
        );

        if (keepId > 0) {
            for (int i = 0; i < userIds.size(); i++) {
                if (userIds.get(i) == keepId) {
                    userFilter.setSelection(i);
                    break;
                }
            }
        }
    }

    private void render() {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        renderSummary();

        TextView title = text("فعالیت‌ها", 17, ink, true);
        title.setGravity(Gravity.RIGHT);
        title.setPadding(dp(2), dp(18), dp(2), dp(10));
        content.addView(title);

        if (logs.isEmpty()) {
            TextView empty = text(
                    "فعالیتی مطابق فیلتر انتخاب‌شده پیدا نشد.",
                    13,
                    muted,
                    false
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(
                    dp(10),
                    dp(28),
                    dp(10),
                    dp(28)
            );
            content.addView(empty);
            return;
        }

        for (JSONObject log : logs) {
            content.addView(logCard(log));
        }

        if (nextBeforeId != null) {
            Button more =
                    secondaryButton("بارگذاری فعالیت‌های قدیمی‌تر");
            more.setOnClickListener(v -> load(true));

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(50)
                    );
            lp.topMargin = dp(8);
            content.addView(more, lp);
        }
    }

    private void renderSummary() {
        LinearLayout card = card();

        TextView title =
                text("خلاصه مرکز فعالیت‌ها", 16, ink, true);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        addRow(
                card,
                "فعالیت مطابق فیلتر",
                number(
                        summary.optLong(
                                "matched_count",
                                0L
                        )
                ),
                turquoise
        );
        addRow(
                card,
                "فعالیت امروز",
                number(
                        summary.optLong(
                                "today_count",
                                0L
                        )
                ),
                ink
        );
        addRow(
                card,
                "کاربر درگیر",
                number(
                        summary.optLong(
                                "active_users",
                                0L
                        )
                ),
                brown
        );
        addRow(
                card,
                "عملیات حساس",
                number(
                        summary.optLong(
                                "critical_count",
                                0L
                        )
                ),
                summary.optLong(
                        "critical_count",
                        0L
                ) > 0 ? red : green
        );

        content.addView(card);
    }

    private View logCard(JSONObject log) {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        header.addView(
                texts,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        String action =
                log.optString("action", "");
        TextView actionView =
                text(actionLabel(action), 14, ink, true);
        actionView.setGravity(Gravity.RIGHT);
        texts.addView(actionView);

        String user =
                log.optString("user_name", "");
        String created =
                log.optString("created_at", "");

        TextView meta = text(
                (user.isEmpty()
                        ? "سیستم"
                        : user) +
                        (created.isEmpty()
                                ? ""
                                : " • " +
                                JalaliDateTime
                                        .formatUtcCompact(
                                                created
                                        )),
                10,
                muted,
                false
        );
        meta.setGravity(Gravity.RIGHT);
        meta.setPadding(0, dp(5), 0, 0);
        texts.addView(meta);

        String severity =
                log.optString("severity", "normal");
        TextView badge = text(
                severityLabel(severity),
                9,
                severityColor(severity),
                true
        );
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(
                dp(9),
                dp(6),
                dp(9),
                dp(6)
        );
        badge.setBackground(
                rounded(
                        severityBg(severity),
                        14
                )
        );
        header.addView(badge);

        card.addView(header);

        String entity =
                entityLabel(
                        log.optString(
                                "entity_type",
                                ""
                        )
                );
        long entityId =
                log.optLong("entity_id", 0L);

        if (!entity.isEmpty()) {
            TextView entityView = text(
                    entity +
                            (entityId > 0
                                    ? " #" +
                                    number(entityId)
                                    : ""),
                    10,
                    brown,
                    false
            );
            entityView.setGravity(Gravity.RIGHT);
            entityView.setPadding(
                    0,
                    dp(7),
                    0,
                    0
            );
            card.addView(entityView);
        }

        String details =
                detailsPreview(
                        log.optString(
                                "details",
                                ""
                        )
                );
        if (!details.isEmpty()) {
            TextView detailView =
                    text(details, 10, muted, false);
            detailView.setGravity(Gravity.RIGHT);
            detailView.setPadding(
                    0,
                    dp(6),
                    0,
                    0
            );
            card.addView(detailView);
        }

        card.setOnClickListener(
                v -> showLogDetail(log)
        );
        return card;
    }

    private void showLogDetail(JSONObject log) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(
                dp(18),
                dp(10),
                dp(18),
                dp(18)
        );

        addDetail(
                box,
                "فعالیت",
                actionLabel(
                        log.optString(
                                "action",
                                ""
                        )
                )
        );
        addDetail(
                box,
                "کاربر",
                log.optString(
                        "user_name",
                        "سیستم"
                ) +
                        " • " +
                        roleLabel(
                                log.optString(
                                        "user_role",
                                        ""
                                )
                        )
        );

        String created =
                log.optString("created_at", "");
        if (!created.isEmpty()) {
            addDetail(
                    box,
                    "زمان",
                    JalaliDateTime
                            .formatUtcFull(created)
            );
        }

        String entity =
                entityLabel(
                        log.optString(
                                "entity_type",
                                ""
                        )
                );
        if (!entity.isEmpty()) {
            long id =
                    log.optLong("entity_id", 0L);
            addDetail(
                    box,
                    "مرتبط با",
                    entity +
                            (id > 0
                                    ? " #" +
                                    number(id)
                                    : "")
            );
        }

        String details =
                formatDetails(
                        log.optString(
                                "details",
                                ""
                        )
                );

        if (!details.isEmpty()) {
            TextView detailsTitle =
                    text("جزئیات ثبت‌شده", 12, ink, true);
            detailsTitle.setGravity(Gravity.RIGHT);
            detailsTitle.setPadding(
                    0,
                    dp(12),
                    0,
                    dp(5)
            );
            box.addView(detailsTitle);

            TextView detailsBody =
                    text(details, 11, muted, false);
            detailsBody.setGravity(Gravity.RIGHT);
            box.addView(detailsBody);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        new AlertDialog.Builder(this)
                .setTitle(
                        severityLabel(
                                log.optString(
                                        "severity",
                                        "normal"
                                )
                        )
                )
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .show();
    }

    private String detailsPreview(String raw) {
        String formatted = formatDetails(raw);
        if (formatted.length() > 180) {
            return formatted.substring(0, 180) + "…";
        }
        return formatted;
    }

    private String formatDetails(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }

        try {
            JSONObject json = new JSONObject(raw);
            StringBuilder out = new StringBuilder();
            Iterator<String> keys = json.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                Object value = json.opt(key);
                if (value == null ||
                        value == JSONObject.NULL) {
                    continue;
                }

                if (out.length() > 0) {
                    out.append("\n");
                }

                out.append(detailKeyLabel(key))
                        .append(": ")
                        .append(formatDetailValue(
                                key,
                                value
                        ));
            }

            return out.toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String formatDetailValue(
            String key,
            Object value
    ) {
        if (value instanceof Boolean) {
            return (Boolean) value
                    ? "بله"
                    : "خیر";
        }

        if (key.contains("amount") ||
                key.contains("price") ||
                key.contains("cost") ||
                key.contains("cash") ||
                key.contains("balance") ||
                key.contains("total") ||
                key.contains("discount")) {
            try {
                return money(
                        Long.parseLong(
                                String.valueOf(value)
                        )
                );
            } catch (Exception ignored) {
            }
        }

        return JalaliDateTime.fa(
                String.valueOf(value)
        );
    }

    private String detailKeyLabel(String key) {
        if ("table_id".equals(key)) return "میز";
        if ("order_id".equals(key)) return "سفارش";
        if ("amount".equals(key)) return "مبلغ";
        if ("opening_cash".equals(key)) return "موجودی اولیه";
        if ("counted_cash".equals(key)) return "موجودی واقعی";
        if ("payment_method".equals(key)) return "روش پرداخت";
        if ("name".equals(key)) return "نام";
        if ("username".equals(key)) return "نام کاربری";
        if ("role".equals(key)) return "نقش";
        if ("active".equals(key)) return "فعال";
        if ("password_changed".equals(key)) return "رمز تغییر کرد";
        if ("reason".equals(key)) return "دلیل";
        if ("note".equals(key)) return "توضیح";
        if ("qty".equals(key)) return "تعداد";
        if ("from_table_id".equals(key)) return "میز مبدا";
        if ("to_table_id".equals(key)) return "میز مقصد";
        if ("credit_limit".equals(key)) return "سقف اعتبار";
        if ("balance_before".equals(key)) return "مانده قبل";
        if ("balance_after".equals(key)) return "مانده بعد";
        if ("direction".equals(key)) return "جهت اصلاح";
        if ("permissions".equals(key)) return "مجوزها";
        if ("backup_id".equals(key)) return "شناسه بکاپ";
        if ("safety_backup_id".equals(key)) return "Snapshot ایمنی";
        if ("restored_tables".equals(key)) return "جدول بازیابی‌شده";
        if ("row_count".equals(key)) return "تعداد ردیف";
        if ("size_bytes".equals(key)) return "حجم بکاپ";
        return key.replace("_", " ");
    }

    private void showJalaliDateDialog() {
        LocalDate base =
                selectedDate == null
                        ? JalaliDateTime
                        .iranNow()
                        .toLocalDate()
                        : selectedDate;

        int[] j =
                JalaliDateTime
                        .gregorianToJalali(
                                base.getYear(),
                                base.getMonthValue(),
                                base.getDayOfMonth()
                        );

        EditText input = field(
                "مثلاً ۱۴۰۵/۰۶/۳۰"
        );
        input.setText(
                JalaliDateTime.fa(
                        j[0] + "/" +
                                two(j[1]) + "/" +
                                two(j[2])
                )
        );
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
        );

        new AlertDialog.Builder(this)
                .setTitle("فیلتر تاریخ شمسی")
                .setView(input)
                .setPositiveButton(
                        "اعمال",
                        (d,w) -> {
                            try {
                                String raw =
                                        latinDigits(
                                                input.getText()
                                                        .toString()
                                        )
                                                .replace(
                                                        "-",
                                                        "/"
                                                )
                                                .trim();

                                String[] parts =
                                        raw.split("/");
                                if (parts.length != 3) {
                                    throw new IllegalArgumentException();
                                }

                                int jy =
                                        Integer.parseInt(
                                                parts[0].trim()
                                        );
                                int jm =
                                        Integer.parseInt(
                                                parts[1].trim()
                                        );
                                int jd =
                                        Integer.parseInt(
                                                parts[2].trim()
                                        );

                                int[] g =
                                        JalaliDateTime
                                                .jalaliToGregorian(
                                                        jy,
                                                        jm,
                                                        jd
                                                );

                                LocalDate selected =
                                        LocalDate.of(
                                                g[0],
                                                g[1],
                                                g[2]
                                        );

                                int[] verify =
                                        JalaliDateTime
                                                .gregorianToJalali(
                                                        selected.getYear(),
                                                        selected.getMonthValue(),
                                                        selected.getDayOfMonth()
                                                );

                                if (verify[0] != jy ||
                                        verify[1] != jm ||
                                        verify[2] != jd) {
                                    throw new IllegalArgumentException();
                                }

                                selectedDate = selected;
                                allDates = false;
                                updateDateText();
                                load(false);
                            } catch (Exception e) {
                                showError(
                                        "تاریخ شمسی معتبر نیست."
                                );
                            }
                        }
                )
                .setNegativeButton("لغو", null)
                .show();
    }

    private void updateDateText() {
        if (dateFilterView == null) return;

        if (allDates) {
            dateFilterView.setText(
                    "بازه: همه تاریخ‌ها"
            );
            return;
        }

        LocalDate date =
                selectedDate == null
                        ? JalaliDateTime
                        .iranNow()
                        .toLocalDate()
                        : selectedDate;

        int[] j =
                JalaliDateTime
                        .gregorianToJalali(
                                date.getYear(),
                                date.getMonthValue(),
                                date.getDayOfMonth()
                        );

        dateFilterView.setText(
                "تاریخ: " +
                        JalaliDateTime.fa(
                                j[0] + "/" +
                                        two(j[1]) + "/" +
                                        two(j[2])
                        )
        );
    }

    private String categoryKey(int position) {
        switch (position) {
            case 1: return "sales";
            case 2: return "finance";
            case 3: return "inventory";
            case 4: return "catalog";
            case 5: return "customers";
            case 6: return "security";
            case 7: return "system";
            default: return "";
        }
    }

    private String actionLabel(String action) {
        if ("setup_user".equals(action)) return "راه‌اندازی کاربر";
        if ("login".equals(action)) return "ورود به حساب";
        if ("create_user".equals(action)) return "ساخت کاربر";
        if ("update_user".equals(action)) return "ویرایش کاربر";
        if ("update_role_permissions".equals(action)) return "تغییر مجوز نقش";
        if ("open_shift".equals(action)) return "شروع شیفت";
        if ("close_shift".equals(action)) return "پایان شیفت";
        if ("open_order".equals(action)) return "باز کردن سفارش";
        if ("add_order_item".equals(action)) return "افزودن آیتم سفارش";
        if ("update_order_item_qty".equals(action)) return "تغییر تعداد آیتم";
        if ("delete_order_item".equals(action)) return "حذف آیتم سفارش";
        if ("settle_order".equals(action)) return "تسویه سفارش";
        if ("reverse_settlement".equals(action)) return "برگرداندن تسویه";
        if ("cancel_order".equals(action)) return "لغو سفارش";
        if ("merge_orders".equals(action)) return "ادغام سفارش";
        if ("transfer_order".equals(action)) return "انتقال سفارش";
        if ("hard_delete_order".equals(action)) return "حذف کامل سفارش";
        if ("create_expense".equals(action)) return "ثبت هزینه";
        if ("customer_payment".equals(action)) return "وصول بدهی مشتری";
        if ("customer_ledger_adjustment".equals(action)) return "اصلاح دستی حساب مشتری";
        if ("create_customer".equals(action)) return "ساخت مشتری";
        if ("update_customer".equals(action)) return "ویرایش مشتری";
        if ("create_inventory_item".equals(action)) return "ساخت کالای انبار";
        if ("update_inventory_item".equals(action)) return "ویرایش کالای انبار";
        if ("inventory_movement".equals(action)) return "گردش دستی انبار";
        if ("create_inventory_link".equals(action)) return "اتصال منو به انبار";
        if ("delete_inventory_link".equals(action)) return "حذف اتصال انبار";
        if ("update_recipe".equals(action)) return "ویرایش فرمول مصرف";
        if ("create_catalog_category".equals(action)) return "ساخت دسته منو";
        if ("update_catalog_category".equals(action)) return "ویرایش دسته منو";
        if ("create_catalog_item".equals(action)) return "ساخت آیتم منو";
        if ("update_catalog_item".equals(action)) return "ویرایش آیتم منو";
        if ("create_hookah".equals(action)) return "ساخت قلیان";
        if ("create_table".equals(action)) return "ساخت میز";
        if ("create_backup".equals(action)) return "ساخت بکاپ دستی";
        if ("scheduled_backup".equals(action)) return "بکاپ خودکار روزانه";
        if ("restore_backup".equals(action)) return "بازیابی بکاپ";
        if ("delete_backup".equals(action)) return "حذف بکاپ";
        return action == null || action.isEmpty()
                ? "فعالیت"
                : action;
    }

    private String entityLabel(String entity) {
        if ("order".equals(entity)) return "سفارش";
        if ("order_item".equals(entity)) return "آیتم سفارش";
        if ("user".equals(entity)) return "کاربر";
        if ("customer".equals(entity)) return "مشتری";
        if ("expense".equals(entity)) return "هزینه";
        if ("cash_shift".equals(entity)) return "شیفت";
        if ("inventory_item".equals(entity)) return "کالای انبار";
        if ("inventory_link".equals(entity)) return "اتصال انبار";
        if ("catalog_category".equals(entity)) return "دسته منو";
        if ("hookah".equals(entity)) return "قلیان";
        if ("drink".equals(entity)) return "نوشیدنی";
        if ("food".equals(entity)) return "خوراکی";
        if ("service".equals(entity)) return "خدمت";
        if ("table".equals(entity)) return "میز";
        if ("role".equals(entity)) return "نقش";
        if ("backup_snapshot".equals(entity)) return "نسخه پشتیبان";
        return entity == null ? "" : entity;
    }

    private String severityLabel(String severity) {
        if ("critical".equals(severity)) return "حساس";
        if ("attention".equals(severity)) return "مهم";
        return "عادی";
    }

    private int severityColor(String severity) {
        if ("critical".equals(severity)) return red;
        if ("attention".equals(severity)) return brown;
        return green;
    }

    private int severityBg(String severity) {
        if ("critical".equals(severity)) {
            return Color.rgb(250, 235, 232);
        }
        if ("attention".equals(severity)) {
            return softGold;
        }
        return Color.rgb(232, 243, 235);
    }

    private String roleLabel(String role) {
        if ("admin".equals(role)) return "مدیر";
        if ("cashier".equals(role)) return "صندوق‌دار";
        if ("staff".equals(role)) return "شاگرد";
        return "سیستم";
    }

    private void addRow(
            LinearLayout parent,
            String label,
            String value,
            int color
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView left =
                text(label, 11, muted, false);
        left.setGravity(Gravity.RIGHT);
        row.addView(
                left,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView right =
                text(value, 12, color, true);
        row.addView(right);

        parent.addView(row);
    }

    private void addDetail(
            LinearLayout parent,
            String label,
            String value
    ) {
        TextView row =
                text(label + ": " + value, 11, ink, false);
        row.setGravity(Gravity.RIGHT);
        row.setPadding(0, dp(5), 0, dp(5));
        parent.addView(row);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(14)
        );
        c.setBackground(
                rounded(surface, 19)
        );
        c.setElevation(dp(1));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        lp.bottomMargin = dp(9);
        c.setLayoutParams(lp);

        return c;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(ink);
        e.setHintTextColor(muted);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setBackground(
                rounded(
                        Color.rgb(250, 248, 244),
                        14
                )
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)
                );
        lp.bottomMargin = dp(9);
        e.setLayoutParams(lp);

        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        b.setBackground(
                rounded(turquoise, 16)
        );
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setTextColor(brown);
        b.setAllCaps(false);
        b.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        b.setBackground(
                rounded(softGold, 16)
        );
        return b;
    }

    private TextView text(
            String value,
            int sp,
            int color,
            boolean bold
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(
                Typeface.create(
                        "sans-serif",
                        bold
                                ? Typeface.BOLD
                                : Typeface.NORMAL
                )
        );
        return t;
    }

    private GradientDrawable rounded(
            int color,
            int radiusDp
    ) {
        GradientDrawable d =
                new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private String money(long value) {
        return JalaliDateTime.fa(
                String.format(
                        Locale.US,
                        "%,d تومان",
                        value
                )
        );
    }

    private String number(long value) {
        return JalaliDateTime.fa(
                String.format(
                        Locale.US,
                        "%,d",
                        value
                )
        );
    }

    private String two(int value) {
        return value < 10
                ? "0" + value
                : String.valueOf(value);
    }

    private String latinDigits(String value) {
        if (value == null) return "";

        String result = value;
        String fa = "۰۱۲۳۴۵۶۷۸۹";
        String ar = "٠١٢٣٤٥٦٧٨٩";

        for (int i = 0; i < 10; i++) {
            result = result.replace(
                    fa.charAt(i),
                    (char) ('0' + i)
            );
            result = result.replace(
                    ar.charAt(i),
                    (char) ('0' + i)
            );
        }

        return result;
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(
                this,
                message == null ||
                        message.trim().isEmpty()
                        ? "خطا در دریافت فعالیت‌ها"
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
