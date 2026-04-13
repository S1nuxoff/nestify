package com.nestify.tvshell;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class ProgressReporter {
    private static final String TAG = "NestifyProgress";
    private static final long INTERVAL_MS = 5000L;
    private static final long MIN_DELTA_MS = 3000L;

    private static long lastSentPosMs = -1L;
    private static Thread thread;
    private static volatile boolean running = false;
    private static Context appContext;

    private ProgressReporter() {
    }

    interface StatusProvider {
        JSONObject getStatus();
    }

    static synchronized void start(Context context, StatusProvider provider) {
        appContext = context.getApplicationContext();
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(() -> {
            while (running) {
                try {
                    sendProgress(provider.getStatus(), false);
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    static synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        lastSentPosMs = -1L;
    }

    static void reportImmediate(JSONObject status) {
        dispatch(status, true);
    }

    static void initialize(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private static void dispatch(JSONObject status, boolean force) {
        new Thread(() -> sendProgress(status, force), "nestify-progress-once").start();
    }

    static void syncPosition(long positionMs) {
        lastSentPosMs = positionMs;
    }

    private static void sendProgress(JSONObject st, boolean force) {
        if (st == null) {
            Log.d(TAG, "skip: status is null");
            return;
        }

        int userId = parseInt(st.opt("user_id"));
        if (userId <= 0) {
            Log.d(TAG, "skip: invalid user_id=" + st.opt("user_id"));
            return;
        }

        String movieId = st.optString("movie_id", "");
        if (movieId.isBlank()) {
            Log.d(TAG, "skip: movie_id is blank");
            return;
        }

        long durationMs = st.optLong("duration_ms", 0L);
        Integer durationSeconds = durationMs > 0 ? (int) (durationMs / 1000L) : null;

        boolean isPlaying = st.optBoolean("is_playing", false);
        long positionMs = st.optLong("position_ms", 0L);

        if (!force && !isPlaying) {
            Log.d(TAG, "skip: not playing");
            return;
        }

        if (!force && lastSentPosMs >= 0 && Math.abs(positionMs - lastSentPosMs) < MIN_DELTA_MS) {
            Log.d(TAG, "skip: delta too small positionMs=" + positionMs + " lastSentPosMs=" + lastSentPosMs);
            return;
        }

        HttpURLConnection conn = null;
        try {
            if (appContext == null) {
                Log.d(TAG, "skip: appContext is null");
                return;
            }
            URL url = new URL(ServerConfig.getBackendBaseUrl(appContext) + "/api/v3/watch/progress");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JSONObject body = new JSONObject()
                .put("user_id", userId)
                .put("movie_id", movieId)
                .put("position_seconds", positionMs / 1000)
                .put("duration", durationSeconds != null ? durationSeconds : JSONObject.NULL)
                .put("season", st.has("season") ? st.opt("season") : JSONObject.NULL)
                .put("episode", st.has("episode") ? st.opt("episode") : JSONObject.NULL)
                .put("torrent_hash", optOrNull(st, "torrent_hash"))
                .put("torrent_file_id", st.has("torrent_file_id") ? st.opt("torrent_file_id") : JSONObject.NULL)
                .put("torrent_fname", optOrNull(st, "torrent_fname"))
                .put("torrent_magnet", optOrNull(st, "torrent_magnet"));

            byte[] bytes = body.toString().getBytes();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "sent: code=" + code + " user_id=" + userId + " movie_id=" + movieId + " position=" + (positionMs / 1000));
            lastSentPosMs = positionMs;
        } catch (Exception e) {
            Log.e(TAG, "send failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static Object optOrNull(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return JSONObject.NULL;
        }
        String value = obj.optString(key, "");
        return value.isBlank() ? JSONObject.NULL : value;
    }

    private static int parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (Exception ignored) {
                return -1;
            }
        }
        return -1;
    }
}
