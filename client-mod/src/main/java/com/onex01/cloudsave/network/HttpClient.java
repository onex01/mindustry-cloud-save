package com.onex01.cloudsave.network;

import arc.util.Log;
import arc.util.serialization.Jval;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpClient {
    
    public interface Callback {
        void onSuccess(int statusCode, String response);
        void onError(String error);
    }
    
    public static void get(String url, String token, Callback callback) {
        new Thread(() -> {
            try {
                Log.info("[HttpClient] GET: " + url);
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                
                int responseCode = conn.getResponseCode();
                Log.info("[HttpClient] Ответ: " + responseCode);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                callback.onSuccess(responseCode, response.toString());
                
            } catch (Exception e) {
                Log.err("[HttpClient] Ошибка GET: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }
    
    public static void post(String url, String json, String token, Callback callback) {
        new Thread(() -> {
            try {
                Log.info("[HttpClient] POST: " + url);
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
                int responseCode = conn.getResponseCode();
                Log.info("[HttpClient] Ответ: " + responseCode);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                callback.onSuccess(responseCode, response.toString());
                
            } catch (Exception e) {
                Log.err("[HttpClient] Ошибка POST: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }
}