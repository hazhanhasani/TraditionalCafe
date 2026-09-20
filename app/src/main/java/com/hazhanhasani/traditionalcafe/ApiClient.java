package com.hazhanhasani.traditionalcafe;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {

    public static final String BASE =
            "https://traditionalcafe.hazhanhasani4268-0f9.workers.dev";

    private ApiClient() {}

    public static JSONObject get(Context context, String path) throws Exception {
        return request(context, "GET", path, null);
    }

    public static JSONObject post(Context context, String path, JSONObject body) throws Exception {
        return request(context, "POST", path, body == null ? new JSONObject() : body);
    }

    public static JSONObject patch(Context context, String path, JSONObject body) throws Exception {
        return request(context, "PATCH", path, body == null ? new JSONObject() : body);
    }

    public static JSONObject request(Context context, String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE + path).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");

            String token = context.getSharedPreferences("session", Context.MODE_PRIVATE)
                    .getString("token", "");
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null && !"GET".equals(method)) {
                connection.setDoOutput(true);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
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

            JSONObject response = text.length() == 0
                    ? new JSONObject()
                    : new JSONObject(text.toString());

            if (status < 200 || status >= 300) {
                String message = response.optString("message", "خطای ارتباط با سرور");
                throw new ApiException(status, response.optString("error", "api_error"), message);
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static class ApiException extends Exception {
        public final int status;
        public final String code;

        public ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
