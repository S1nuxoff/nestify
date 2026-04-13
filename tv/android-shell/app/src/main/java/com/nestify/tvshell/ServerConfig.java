package com.nestify.tvshell;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.net.URL;

final class ServerConfig {
    private static final String PREFS = "nestify_server_config";
    private static final String KEY_START_URL = "start_url";
    private static final String KEY_BACKEND_URL = "backend_url";

    private ServerConfig() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getStartUrl(Context context) {
        String saved = prefs(context).getString(KEY_START_URL, "");
        return saved != null && !saved.isBlank() ? normalizeHttpUrl(saved) : normalizeHttpUrl(BuildConfig.START_URL);
    }

    static String getBackendBaseUrl(Context context) {
        String saved = prefs(context).getString(KEY_BACKEND_URL, "");
        return saved != null && !saved.isBlank() ? normalizeBackendUrl(saved) : normalizeBackendUrl(BuildConfig.BACKEND_BASE_URL);
    }

    static String getWsBaseUrl(Context context) {
        return deriveWsUrl(getBackendBaseUrl(context), BuildConfig.WS_BASE_URL);
    }

    static void save(Context context, String startUrl, String backendUrl) {
        prefs(context)
            .edit()
            .putString(KEY_START_URL, normalizeHttpUrl(startUrl))
            .putString(KEY_BACKEND_URL, normalizeBackendUrl(backendUrl))
            .apply();
    }

    static void reset(Context context) {
        prefs(context)
            .edit()
            .remove(KEY_START_URL)
            .remove(KEY_BACKEND_URL)
            .apply();
    }

    static JSONObject toJson(Context context) {
        try {
            return new JSONObject()
                .put("startUrl", getStartUrl(context))
                .put("backendUrl", getBackendBaseUrl(context))
                .put("wsUrl", getWsBaseUrl(context))
                .put("usesDefault", isDefault(context));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static boolean isDefault(Context context) {
        SharedPreferences prefs = prefs(context);
        return !prefs.contains(KEY_START_URL) && !prefs.contains(KEY_BACKEND_URL);
    }

    static String normalizeHttpUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isBlank()) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        return value.replaceAll("/+$", "") + "/";
    }

    static String normalizeBackendUrl(String raw) {
        String value = normalizeHttpUrl(raw);
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String deriveWsUrl(String backendUrl, String fallback) {
        try {
            String normalized = normalizeBackendUrl(backendUrl);
            URL url = new URL(normalized);
            String scheme = "https".equalsIgnoreCase(url.getProtocol()) ? "wss" : "ws";
            StringBuilder out = new StringBuilder();
            out.append(scheme).append("://").append(url.getHost());
            if (url.getPort() != -1) {
                out.append(":").append(url.getPort());
            }
            return out.toString();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
