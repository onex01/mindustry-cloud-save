package com.onex01.cloudsave.util;

import arc.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mindustry.Vars;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {
    
    private static final String CONFIG_FILE = "cloud-save-config.json";
    private Config config;
    private final Gson gson;
    
    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadConfig();
    }
    
    private void loadConfig() {
        File configFile = getConfigFile();
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config = gson.fromJson(reader, Config.class);
                Log.info("[CloudSave] Конфигурация загружена");
            } catch (IOException e) {
                Log.err("[CloudSave] Ошибка загрузки конфигурации: " + e.getMessage());
                config = new Config();
            }
        } else {
            Log.info("[CloudSave] Конфигурация не найдена, создание новой...");
            config = new Config();
            saveConfig();
        }
    }
    
    public void saveConfig() {
        File configFile = getConfigFile();
        
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
            Log.info("[CloudSave] Конфигурация сохранена");
        } catch (IOException e) {
            Log.err("[CloudSave] Ошибка сохранения конфигурации: " + e.getMessage());
        }
    }
    
    private File getConfigFile() {
        return new File(Vars.dataDirectory.file().getParentFile(), CONFIG_FILE);
    }
    
    public String getServerUrl() {
        return config.serverUrl;
    }
    
    public void setServerUrl(String url) {
        config.serverUrl = url;
        saveConfig();
    }
    
    public String getAuthToken() {
        return config.authToken;
    }
    
    public void setAuthToken(String token) {
        config.authToken = token;
        saveConfig();
    }
    
    public String getUsername() {
        return config.username;
    }
    
    public void setUsername(String username) {
        config.username = username;
        saveConfig();
    }
    
    public boolean isLoggedIn() {
        return config.authToken != null && !config.authToken.isEmpty();
    }
    
    public void logout() {
        config.authToken = null;
        config.username = null;
        saveConfig();
    }
    
    // Внутренний класс для конфигурации
    private static class Config {
        String serverUrl = "http://localhost:3000";
        String authToken = null;
        String username = null;
    }
}