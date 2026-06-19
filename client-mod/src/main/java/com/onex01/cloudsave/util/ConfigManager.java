package com.onex01.cloudsave.util;

import arc.util.Log;
import arc.util.io.Streams;
import mindustry.Vars;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {
    
    private static final String CONFIG_FILE = "cloud-save-config.json";
    private String serverUrl = "http://localhost:3000";
    private String authToken = null;
    private String username = null;
    private boolean debugMode = false;
    
    public ConfigManager() {
        loadConfig();
    }
    
    private void loadConfig() {
        File configFile = getConfigFile();
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                StringBuilder content = new StringBuilder();
                int ch;
                while ((ch = reader.read()) != -1) {
                    content.append((char) ch);
                }
                
                String json = content.toString();
                // Простой парсинг JSON без библиотек
                serverUrl = extractValue(json, "serverUrl", "http://localhost:3000");
                authToken = extractValue(json, "authToken", null);
                username = extractValue(json, "username", null);
                debugMode = "true".equals(extractValue(json, "debugMode", "false"));
                
                Log.info("[CloudSave] Конфигурация загружена. Сервер: " + serverUrl);
            } catch (IOException e) {
                Log.err("[CloudSave] Ошибка загрузки конфигурации: " + e.getMessage());
            }
        } else {
            Log.info("[CloudSave] Конфигурация не найдена, создание новой...");
            saveConfig();
        }
    }
    
    private String extractValue(String json, String key, String defaultValue) {
        String searchKey = "\"" + key + "\": \"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return defaultValue;
        
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return defaultValue;
        
        String value = json.substring(startIndex, endIndex);
        return value.equals("null") ? defaultValue : value;
    }
    
    public void saveConfig() {
        File configFile = getConfigFile();
        
        try (FileWriter writer = new FileWriter(configFile)) {
            String json = "{\n";
            json += "  \"serverUrl\": \"" + (serverUrl != null ? serverUrl : "") + "\",\n";
            json += "  \"authToken\": \"" + (authToken != null ? authToken : "null") + "\",\n";
            json += "  \"username\": \"" + (username != null ? username : "null") + "\",\n";
            json += "  \"debugMode\": \"" + debugMode + "\"\n";
            json += "}";
            
            writer.write(json);
            Log.info("[CloudSave] Конфигурация сохранена");
        } catch (IOException e) {
            Log.err("[CloudSave] Ошибка сохранения конфигурации: " + e.getMessage());
        }
    }
    
    private File getConfigFile() {
        // Используем Vars.dataDirectory - работает на всех платформах
        File dataDir = Vars.dataDirectory.file();
        
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        return new File(dataDir, CONFIG_FILE);
    }
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public void setServerUrl(String url) {
        this.serverUrl = url;
        saveConfig();
    }
    
    public String getAuthToken() {
        return authToken;
    }
    
    public void setAuthToken(String token) {
        this.authToken = token;
        saveConfig();
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
        saveConfig();
    }
    
    public boolean isLoggedIn() {
        return authToken != null && !authToken.isEmpty() && !authToken.equals("null");
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        saveConfig();
    }
    
    public void logout() {
        this.authToken = null;
        this.username = null;
        saveConfig();
    }
}