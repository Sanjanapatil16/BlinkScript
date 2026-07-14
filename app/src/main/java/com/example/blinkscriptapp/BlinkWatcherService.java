package com.example.blinkscriptapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

// Watches for blink events from two sources:
// 1. Flask API polling (primary, most reliable on emulator)
// 2. blink.txt file in app files dir (ADB fallback)
public class BlinkWatcherService extends Service {

    private static final String BLINK_FILENAME = "blink.txt";
    private static final String FLASK_BLINK_URL = "http://10.0.2.2:5000/api/latest-blink";

    private File blinkFile;
    private Thread pollingThread;
    private boolean isRunning = false;
    private int lastFlaskBlinkId = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        blinkFile = new File(getFilesDir(), BLINK_FILENAME);
        android.util.Log.d("BlinkWatcher", "Service started. Watching: " + blinkFile.getAbsolutePath());

        isRunning = true;
        pollingThread = new Thread(() -> {
            while (isRunning) {
                pollFlaskForBlink();
                checkBlinkFile();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        pollingThread.start();
    }

    // Primary: poll Flask server for new blink events from Python camera script
    private void pollFlaskForBlink() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(FLASK_BLINK_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(400);
            conn.setReadTimeout(400);

            int code = conn.getResponseCode();
            if (code != 200) return;

            String body = readStream(conn);
            int eventId = extractJsonInt(body, "id");
            int count = extractJsonInt(body, "count");

            if (eventId > lastFlaskBlinkId && count > 0) {
                lastFlaskBlinkId = eventId;
                int option = mapBlinkCountToOption(count);
                android.util.Log.d("BlinkWatcher", "Flask blink: count=" + count + " -> option=" + option);
                broadcastOption(option);
            }
        } catch (Exception ignored) {
            // Flask not running yet — ADB file fallback still works
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // Fallback: read blink.txt pushed via ADB
    private void checkBlinkFile() {
        if (!blinkFile.exists()) return;

        String content = readBlinkFile();
        blinkFile.delete();

        if (content == null || content.trim().isEmpty()) return;

        String trimmed = content.trim();
        android.util.Log.d("BlinkWatcher", "File blink: " + trimmed);

        if (trimmed.equalsIgnoreCase("EMERGENCY")) {
            broadcastOption(5);
            return;
        }

        try {
            int value = Integer.parseInt(trimmed);
            broadcastOption(mapBlinkCountToOption(value));
        } catch (NumberFormatException e) {
            android.util.Log.w("BlinkWatcher", "Unknown blink content: " + trimmed);
        }
    }

    // Map raw blink count to UI option number
    // 1 blink = option 1, 2 blinks = option 2, etc.
    private int mapBlinkCountToOption(int count) {
        if (count >= 1 && count <= 4) return count;
        if (count == 5) return 6; // 5 blinks = open typing mode
        return 5; // 6+ blinks = emergency
    }

    private String readBlinkFile() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(blinkFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }

    private void broadcastOption(int option) {
        android.util.Log.d("BlinkWatcher", "Broadcasting option: " + option);
        Intent intent = new Intent("com.example.blinkscriptapp.BLINK_ACTION");
        intent.putExtra("option", option);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private String readStream(HttpURLConnection conn) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private int extractJsonInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        int start = idx + search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (pollingThread != null) pollingThread.interrupt();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
