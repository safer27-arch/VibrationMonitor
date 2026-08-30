package com.example.vibrationmonitor;

public class TelegramSender {
    public interface Callback {
        void onResult(boolean success, String message);
    }

    public static void sendMessage(String token, String chatId, String message, Callback callback) {
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                String urlText = "https://api.telegram.org/bot" + token + "/sendMessage?chat_id=" + java.net.URLEncoder.encode(chatId, "UTF-8") + "&text=" + java.net.URLEncoder.encode(message, "UTF-8");
                java.net.URL url = new java.net.URL(urlText);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                callback.onResult(code >= 200 && code < 300, "HTTP " + code);
            } catch (Exception e) {
                callback.onResult(false, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
