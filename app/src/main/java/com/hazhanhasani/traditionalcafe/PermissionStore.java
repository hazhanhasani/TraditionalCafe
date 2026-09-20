package com.hazhanhasani.traditionalcafe;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

public final class PermissionStore {
    private static final String PREFS = "session";
    private static final String KEY = "permissions_json";

    private PermissionStore() {}

    public static boolean save(Context context, JSONObject permissions) {
        if (permissions == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String oldValue = prefs.getString(KEY, "");
        String newValue = permissions.toString();
        if (newValue.equals(oldValue)) return false;
        prefs.edit().putString(KEY, newValue).apply();
        return true;
    }

    public static boolean has(Context context, String permission) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String role = prefs.getString("role", "staff");
        if ("admin".equals(role)) return true;
        if ("staff".equals(role)) return false;

        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.trim().isEmpty()) return "cashier".equals(role);

        try {
            JSONObject permissions = new JSONObject(raw);
            return permissions.optBoolean(permission, "cashier".equals(role));
        } catch (Exception ignored) {
            return "cashier".equals(role);
        }
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply();
    }
}
