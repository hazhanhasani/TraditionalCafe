package com.hazhanhasani.traditionalcafe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class ReceiptActivity extends Activity {

    private final int bg = Color.rgb(247, 243, 235);
    private final int surface = Color.WHITE;
    private final int ink = Color.rgb(48, 35, 28);
    private final int muted = Color.rgb(126, 115, 105);
    private final int turquoise = Color.rgb(14, 117, 120);
    private final int brown = Color.rgb(92, 57, 35);
    private final int green = Color.rgb(62, 135, 95);
    private final int softGold = Color.rgb(249, 239, 219);

    private long orderId;
    private LinearLayout content;
    private ProgressBar loading;
    private JSONObject receipt;
    private JSONArray items;
    private JSONArray payments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        orderId = getIntent().getLongExtra("order_id", 0L);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        setContentView(buildScreen());
        loadReceipt();
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
        top.addView(
                titles,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView title = text("رسید فروش", 21, ink, true);
        title.setGravity(Gravity.RIGHT);
        titles.addView(title);

        TextView subtitle = text(
                "نسخه مشتری • بدون اطلاعات مالی داخلی",
                11,
                muted,
                false
        );
        subtitle.setGravity(Gravity.RIGHT);
        subtitle.setPadding(0, dp(4), 0, 0);
        titles.addView(subtitle);

        TextView refresh = text("↻", 28, turquoise, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(v -> loadReceipt());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));

        root.addView(top);

        loading = new ProgressBar(this);
        LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadingLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(loading, loadingLp);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(30));

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

    private void loadReceipt() {
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();

        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        "/api/orders/" + orderId + "/receipt"
                );

                receipt = response.optJSONObject("receipt");
                items = response.optJSONArray("items");
                payments = response.optJSONArray("payments");
                if (items == null) items = new JSONArray();
                if (payments == null) payments = new JSONArray();

                runOnUiThread(this::render);
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }

    private void render() {
        loading.setVisibility(View.GONE);
        content.removeAllViews();

        if (receipt == null) {
            showError("اطلاعات رسید پیدا نشد.");
            return;
        }

        LinearLayout header = card();

        TextView business = text(
                receipt.optString("business_name", "کافه سنتی"),
                22,
                brown,
                true
        );
        business.setGravity(Gravity.CENTER);
        header.addView(business);

        TextView number = text(
                "رسید " + receipt.optString("receipt_number", ""),
                13,
                turquoise,
                true
        );
        number.setGravity(Gravity.CENTER);
        number.setPadding(0, dp(7), 0, 0);
        header.addView(number);

        String issuedAt = receipt.optString("issued_at", "");
        if (!issuedAt.isEmpty()) {
            TextView date = text(
                    JalaliDateTime.formatUtcFull(issuedAt),
                    11,
                    muted,
                    false
            );
            date.setGravity(Gravity.CENTER);
            date.setPadding(0, dp(5), 0, 0);
            header.addView(date);
        }

        String phone = receipt.optString("business_phone", "");
        String address = receipt.optString("business_address", "");
        if (!phone.isEmpty()) addCenteredLine(header, phone);
        if (!address.isEmpty()) addCenteredLine(header, address);

        content.addView(header);

        LinearLayout info = card();
        addLine(info, "میز", receipt.optString("table_name", "-"), ink);
        addLine(info, "صندوقدار", receipt.optString("cashier_name", "-"), ink);

        String openedBy = receipt.optString("opened_by_name", "");
        if (!openedBy.isEmpty()) addLine(info, "ثبت‌کننده", openedBy, muted);

        String openedAt = receipt.optString("opened_at", "");
        if (!openedAt.isEmpty()) {
            addLine(
                    info,
                    "شروع سفارش",
                    JalaliDateTime.formatUtcCompact(openedAt),
                    muted
            );
        }
        content.addView(info);

        TextView itemsTitle = text("اقلام", 16, ink, true);
        itemsTitle.setGravity(Gravity.RIGHT);
        itemsTitle.setPadding(dp(2), dp(8), dp(2), dp(8));
        content.addView(itemsTitle);

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            LinearLayout row = card();
            String name = item.optString("name", "مورد");
            long qty = item.optLong("qty", 1L);
            long unitPrice = item.optLong("unit_price", 0L);
            long lineTotal = qty * unitPrice;

            TextView nameView = text(name, 14, ink, true);
            nameView.setGravity(Gravity.RIGHT);
            row.addView(nameView);

            TextView meta = text(
                    number(qty) + " × " + money(unitPrice) +
                            "  =  " + money(lineTotal),
                    11,
                    muted,
                    false
            );
            meta.setGravity(Gravity.RIGHT);
            meta.setPadding(0, dp(5), 0, 0);
            row.addView(meta);

            content.addView(row);
        }

        LinearLayout totals = card();
        addLine(totals, "جمع اقلام", money(receipt.optLong("subtotal", 0L)), ink);

        long discount = receipt.optLong("discount", 0L);
        if (discount > 0) {
            addLine(totals, "تخفیف", "− " + money(discount), green);
        }

        TextView finalTotal = text(
                "مبلغ نهایی: " + money(receipt.optLong("total", 0L)),
                20,
                turquoise,
                true
        );
        finalTotal.setGravity(Gravity.RIGHT);
        finalTotal.setPadding(0, dp(10), 0, 0);
        totals.addView(finalTotal);

        content.addView(totals);

        if (payments.length() > 0) {
            TextView paymentTitle = text("روش پرداخت", 16, ink, true);
            paymentTitle.setGravity(Gravity.RIGHT);
            paymentTitle.setPadding(dp(2), dp(8), dp(2), dp(8));
            content.addView(paymentTitle);

            LinearLayout paymentCard = card();
            for (int i = 0; i < payments.length(); i++) {
                JSONObject payment = payments.optJSONObject(i);
                if (payment == null) continue;

                String label = paymentMethodLabel(payment.optString("method", ""));
                String value = money(payment.optLong("amount", 0L));

                String customer = payment.optString("customer_name", "");
                if (!customer.isEmpty()) {
                    value += " • " + customer;
                }

                addLine(paymentCard, label, value, ink);

                String dueAt = payment.optString("due_at", "");
                if (!dueAt.isEmpty()) {
                    addLine(
                            paymentCard,
                            "سررسید نسیه",
                            JalaliDateTime.formatUtcCompact(dueAt),
                            muted
                    );
                }
            }
            content.addView(paymentCard);
        }

        String notes = receipt.optString("notes", "");
        if (!notes.isEmpty()) {
            LinearLayout noteCard = card();
            TextView note = text("توضیحات: " + notes, 11, muted, false);
            note.setGravity(Gravity.RIGHT);
            noteCard.addView(note);
            content.addView(noteCard);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button share = primaryButton("اشتراک رسید");
        share.setOnClickListener(v -> shareReceipt());
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(50), 1f));

        Button print = secondaryButton("چاپ");
        print.setOnClickListener(v -> printReceipt());
        LinearLayout.LayoutParams printLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        printLp.setMarginStart(dp(8));
        actions.addView(print, printLp);

        content.addView(actions);

        TextView footer = text(
                receipt.optString("footer", "از همراهی شما سپاسگزاریم."),
                11,
                muted,
                false
        );
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(18), dp(8), dp(8));
        content.addView(footer);
    }

    private void shareReceipt() {
        if (receipt == null) return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(
                Intent.EXTRA_SUBJECT,
                "رسید " + receipt.optString("receipt_number", "")
        );
        intent.putExtra(Intent.EXTRA_TEXT, buildPlainReceipt());
        startActivity(Intent.createChooser(intent, "اشتراک رسید"));
    }

    private String buildPlainReceipt() {
        StringBuilder body = new StringBuilder();

        body.append(receipt.optString("business_name", "کافه سنتی"))
                .append("\n")
                .append("رسید ")
                .append(receipt.optString("receipt_number", ""))
                .append("\n");

        String issuedAt = receipt.optString("issued_at", "");
        if (!issuedAt.isEmpty()) {
            body.append(JalaliDateTime.formatUtcFull(issuedAt)).append("\n");
        }

        body.append("میز: ")
                .append(receipt.optString("table_name", "-"))
                .append("\nصندوقدار: ")
                .append(receipt.optString("cashier_name", "-"))
                .append("\n\nاقلام:");

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            long qty = item.optLong("qty", 1L);
            long unit = item.optLong("unit_price", 0L);

            body.append("\n")
                    .append(item.optString("name", "مورد"))
                    .append(" • ")
                    .append(number(qty))
                    .append(" × ")
                    .append(money(unit))
                    .append(" = ")
                    .append(money(qty * unit));
        }

        body.append("\n\nجمع اقلام: ")
                .append(money(receipt.optLong("subtotal", 0L)));

        long discount = receipt.optLong("discount", 0L);
        if (discount > 0) {
            body.append("\nتخفیف: - ").append(money(discount));
        }

        body.append("\nمبلغ نهایی: ")
                .append(money(receipt.optLong("total", 0L)));

        if (payments.length() > 0) {
            body.append("\n\nپرداخت:");
            for (int i = 0; i < payments.length(); i++) {
                JSONObject payment = payments.optJSONObject(i);
                if (payment == null) continue;

                body.append("\n")
                        .append(paymentMethodLabel(payment.optString("method", "")))
                        .append(": ")
                        .append(money(payment.optLong("amount", 0L)));

                String customer = payment.optString("customer_name", "");
                if (!customer.isEmpty()) body.append(" • ").append(customer);
            }
        }

        String footer = receipt.optString("footer", "");
        if (!footer.isEmpty()) body.append("\n\n").append(footer);

        return body.toString();
    }

    private void printReceipt() {
        if (receipt == null) return;

        WebView webView = new WebView(this);
        webView.getSettings().setDefaultTextEncodingName("utf-8");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (printManager == null) {
                    Toast.makeText(
                            ReceiptActivity.this,
                            "سرویس چاپ در این دستگاه در دسترس نیست.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                String jobName = "Receipt-" + receipt.optString("receipt_number", "");
                printManager.print(
                        jobName,
                        view.createPrintDocumentAdapter(jobName),
                        new PrintAttributes.Builder().build()
                );
            }
        });

        webView.loadDataWithBaseURL(
                null,
                buildPrintHtml(),
                "text/html",
                "UTF-8",
                null
        );
    }

    private String buildPrintHtml() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            long qty = item.optLong("qty", 1L);
            long unit = item.optLong("unit_price", 0L);

            rows.append("<tr><td>")
                    .append(escape(item.optString("name", "مورد")))
                    .append("</td><td>")
                    .append(escape(number(qty)))
                    .append("</td><td>")
                    .append(escape(money(qty * unit)))
                    .append("</td></tr>");
        }

        StringBuilder pay = new StringBuilder();
        for (int i = 0; i < payments.length(); i++) {
            JSONObject p = payments.optJSONObject(i);
            if (p == null) continue;

            pay.append("<div>")
                    .append(escape(paymentMethodLabel(p.optString("method", ""))))
                    .append(": ")
                    .append(escape(money(p.optLong("amount", 0L))));

            String customer = p.optString("customer_name", "");
            if (!customer.isEmpty()) pay.append(" • ").append(escape(customer));
            pay.append("</div>");
        }

        String issuedAt = receipt.optString("issued_at", "");
        String date = issuedAt.isEmpty() ? "" : JalaliDateTime.formatUtcFull(issuedAt);

        return "<!doctype html><html dir='rtl'><head><meta charset='utf-8'>" +
                "<style>" +
                "body{font-family:sans-serif;color:#2f231c;padding:16px;font-size:13px}" +
                "h1{text-align:center;font-size:21px;margin:0 0 8px}" +
                ".center{text-align:center}.muted{color:#776d65}" +
                "table{width:100%;border-collapse:collapse;margin:16px 0}" +
                "td,th{border-bottom:1px solid #ddd;padding:8px 4px;text-align:right}" +
                ".total{font-size:18px;font-weight:700;margin-top:12px}" +
                ".box{margin-top:12px;padding-top:10px;border-top:1px dashed #aaa}" +
                "</style></head><body>" +
                "<h1>" + escape(receipt.optString("business_name", "کافه سنتی")) + "</h1>" +
                "<div class='center'>" + escape(receipt.optString("receipt_number", "")) + "</div>" +
                "<div class='center muted'>" + escape(date) + "</div>" +
                "<div class='box'>میز: " + escape(receipt.optString("table_name", "-")) +
                "<br>صندوقدار: " + escape(receipt.optString("cashier_name", "-")) + "</div>" +
                "<table><tr><th>شرح</th><th>تعداد</th><th>مبلغ</th></tr>" +
                rows +
                "</table>" +
                "<div>جمع اقلام: " + escape(money(receipt.optLong("subtotal", 0L))) + "</div>" +
                (receipt.optLong("discount", 0L) > 0
                        ? "<div>تخفیف: - " + escape(money(receipt.optLong("discount", 0L))) + "</div>"
                        : "") +
                "<div class='total'>مبلغ نهایی: " +
                escape(money(receipt.optLong("total", 0L))) +
                "</div>" +
                "<div class='box'>" + pay + "</div>" +
                "<div class='center muted box'>" +
                escape(receipt.optString("footer", "از همراهی شما سپاسگزاریم.")) +
                "</div></body></html>";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String paymentMethodLabel(String method) {
        if ("cash".equals(method)) return "نقدی";
        if ("card".equals(method)) return "کارت / کارتخوان";
        if ("transfer".equals(method)) return "کارت‌به‌کارت";
        if ("credit".equals(method)) return "نسیه / حساب دفتری";
        return method;
    }

    private void addLine(LinearLayout parent, String label, String value, int color) {
        TextView line = text(label + ": " + value, 11, color, false);
        line.setGravity(Gravity.RIGHT);
        line.setPadding(0, dp(5), 0, 0);
        parent.addView(line);
    }

    private void addCenteredLine(LinearLayout parent, String value) {
        TextView line = text(value, 10, muted, false);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, dp(4), 0, 0);
        parent.addView(line);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(surface, 19));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(9);
        card.setLayoutParams(lp);
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
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

    private String money(long value) {
        return JalaliDateTime.fa(String.format(Locale.US, "%,d تومان", value));
    }

    private String number(long value) {
        return JalaliDateTime.fa(String.format(Locale.US, "%,d", value));
    }

    private void showError(String message) {
        loading.setVisibility(View.GONE);
        Toast.makeText(
                this,
                message == null || message.trim().isEmpty()
                        ? "خطا در دریافت رسید."
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
