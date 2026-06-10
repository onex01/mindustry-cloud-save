package com.onex01.cloudsave.network;

import arc.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.onex01.cloudsave.util.ConfigManager;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final Gson gson;
    private final ConfigManager config;
    
    public ApiClient(ConfigManager config) {
        this.config = config;
        this.gson = new Gson();
        
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Регистрация нового пользователя
     */
    public void register(String username, String password, Callback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);
        
        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
            .url(config.getServerUrl() + "/api/auth/register")
            .post(body)
            .build();
        
        executeRequest(request, callback);
    }
    
    /**
     * Вход в систему
     */
    public void login(String username, String password, Callback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);
        
        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
            .url(config.getServerUrl() + "/api/auth/login")
            .post(body)
            .build();
        
        executeRequest(request, new Callback() {
            @Override
            public void onSuccess(String response) {
                // Парсим ответ и сохраняем токен
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
                if (jsonResponse.has("token")) {
                    String token = jsonResponse.get("token").getAsString();
                    config.setAuthToken(token);
                    config.setUsername(username);
                    Log.info("[CloudSave] Успешный вход для пользователя: " + username);
                }
                callback.onSuccess(response);
            }
            
            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }
    
    /**
     * Загрузка сохранения на сервер
     */
    public void uploadSave(File saveFile, String saveName, Callback callback) {
        if (!config.isLoggedIn()) {
            callback.onFailure("Не авторизован");
            return;
        }
        
        RequestBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("saveFile", saveFile.getName(),
                RequestBody.create(saveFile, MediaType.parse("application/octet-stream")))
            .addFormDataPart("name", saveName)
            .build();
        
        Request request = new Request.Builder()
            .url(config.getServerUrl() + "/api/saves")
            .header("Authorization", "Bearer " + config.getAuthToken())
            .post(requestBody)
            .build();
        
        executeRequest(request, callback);
    }
    
    /**
     * Получение списка сохранений
     */
    public void getSaves(Callback callback) {
        if (!config.isLoggedIn()) {
            callback.onFailure("Не авторизован");
            return;
        }
        
        Request request = new Request.Builder()
            .url(config.getServerUrl() + "/api/saves")
            .header("Authorization", "Bearer " + config.getAuthToken())
            .get()
            .build();
        
        executeRequest(request, callback);
    }
    
    /**
     * Скачивание сохранения
     */
    public void downloadSave(int saveId, File destinationFile, Callback callback) {
        if (!config.isLoggedIn()) {
            callback.onFailure("Не авторизован");
            return;
        }
        
        Request request = new Request.Builder()
            .url(config.getServerUrl() + "/api/saves/" + saveId + "/download")
            .header("Authorization", "Bearer " + config.getAuthToken())
            .get()
            .build();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Ошибка сети: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("Ошибка сервера: " + response.code());
                    return;
                }
                
                // Сохраняем файл через byteStream
                try (var inputStream = response.body().byteStream();
                    var outputStream = new java.io.FileOutputStream(destinationFile)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    callback.onSuccess("Файл сохранен: " + destinationFile.getAbsolutePath());
                } catch (Exception e) {
                    callback.onFailure("Ошибка сохранения файла: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Выполнение HTTP запроса
     */
    private void executeRequest(Request request, Callback callback) {
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.err("[CloudSave] Ошибка запроса: " + e.getMessage());
                callback.onFailure("Ошибка сети: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    Log.info("[CloudSave] Успешный ответ: " + responseBody);
                    callback.onSuccess(responseBody);
                } else {
                    Log.err("[CloudSave] Ошибка сервера: " + response.code() + " - " + responseBody);
                    callback.onFailure("Ошибка сервера: " + response.code() + " - " + responseBody);
                }
            }
        });
    }
    
    // Интерфейс для callback
    public interface Callback {
        void onSuccess(String response);
        void onFailure(String error);
    }
}