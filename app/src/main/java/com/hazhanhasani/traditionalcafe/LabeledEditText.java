package com.hazhanhasani.traditionalcafe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * EditText that promotes its hint to a persistent label above the field.
 * Existing screens can keep calling setHint(...); the text no longer
 * disappears when the user enters a value.
 */
public class LabeledEditText extends EditText {

    public static final String FORM_STANDARD = "persistent-labels-v1";

    private CharSequence fieldLabel = "";
    private TextView labelView;
    private boolean labelAttached;

    public LabeledEditText(Context context) {
        super(context);
    }

    public LabeledEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LabeledEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setHint(CharSequence hint) {
        fieldLabel = hint == null ? "" : hint;
        setContentDescription(fieldLabel);
        super.setHint("");
        updateLabel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::ensurePersistentLabel);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        labelAttached = false;
        labelView = null;
    }

    private void ensurePersistentLabel() {
        if (labelAttached || fieldLabel == null || fieldLabel.length() == 0) {
            return;
        }

        if (!(getParent() instanceof LinearLayout)) {
            // Safe fallback for an unexpected container.
            super.setHint(fieldLabel);
            return;
        }

        LinearLayout parent = (LinearLayout) getParent();
        if (parent.getOrientation() != LinearLayout.VERTICAL) {
            super.setHint(fieldLabel);
            return;
        }

        int index = parent.indexOfChild(this);
        if (index < 0) return;

        TextView label = new TextView(getContext());
        label.setText(fieldLabel);
        label.setTextSize(11);
        label.setTextColor(Color.rgb(105, 94, 84));
        label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.setGravity(Gravity.RIGHT);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setIncludeFontPadding(false);
        label.setSingleLine(false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(dp(4), dp(2), dp(4), dp(5));
        label.setLayoutParams(lp);

        parent.addView(label, index);
        labelView = label;
        labelAttached = true;
    }

    private void updateLabel() {
        if (labelView != null) {
            labelView.setText(fieldLabel);
        }
    }

    private int dp(int value) {
        return (int) (
                value *
                getResources().getDisplayMetrics().density
        );
    }
}
