package com.onex01.cloudsave;

import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;
import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import com.onex01.cloudsave.ui.CloudSaveUI;
import com.onex01.cloudsave.util.ConfigManager;

public class CloudSaveMod extends Mod {
    
    private static CloudSaveMod instance;
    private CloudSaveUI cloudSaveUI;
    private ConfigManager configManager;
    
    public CloudSaveMod() {
        instance = this;
        Log.info("[CloudSave] Мод инициализирован!");
    }
    
    @Override
    public void init() {
        Log.info("[CloudSave] Инициализация мода...");
        
        // Загружаем конфигурацию
        configManager = new ConfigManager();
        
        // Инициализируем UI
        cloudSaveUI = new CloudSaveUI();
        
        // Добавляем кнопку в главное меню после загрузки
        Core.app.post(this::addButtonToMainMenu);
        
        Log.info("[CloudSave] Мод успешно загружен!");
    }
    
    @Override
    public void loadContent() {
        // Загрузка контента (если нужно)
    }
    
    /**
     * Добавляет кнопку "Cloud Saves" в главное меню игры
     */
    private void addButtonToMainMenu() {
        try {
            // Получаем главное меню
            if (Core.scene == null) {
                Log.warn("[CloudSave] Scene еще не загружена, повторная попытка через 1 секунду...");
                Core.app.postDelayed(this::addButtonToMainMenu, 60); // 60 фреймов ≈ 1 секунда
                return;
            }
            
            // Ищем контейнер меню
            Table menuTable = findMenuTable();
            
            if (menuTable != null) {
                // Добавляем нашу кнопку
                menuTable.row();
                menuTable.button("☁️ Cloud Saves", () -> {
                    cloudSaveUI.show();
                }).size(200f, 50f).padTop(10f);
                
                Log.info("[CloudSave] Кнопка добавлена в главное меню!");
            } else {
                Log.warn("[CloudSave] Не удалось найти главное меню!");
            }
        } catch (Exception e) {
            Log.err("[CloudSave] Ошибка при добавлении кнопки: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Ищет главную таблицу меню
     */
    private Table findMenuTable() {
        try {
            // Пытаемся найти через Vars.ui.menufrag
            if (mindustry.Vars.ui != null && mindustry.Vars.ui.menufrag != null) {
                return mindustry.Vars.ui.menufrag.cont;
            }
        } catch (Exception e) {
            Log.warn("[CloudSave] Не удалось найти меню через Vars.ui.menufrag");
        }
        
        return null;
    }
    
    public static CloudSaveMod getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public CloudSaveUI getCloudSaveUI() {
        return cloudSaveUI;
    }
}