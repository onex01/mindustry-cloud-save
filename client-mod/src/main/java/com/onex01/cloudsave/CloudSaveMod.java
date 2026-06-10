package com.onex01.cloudsave;

import mindustry.mod.Mod;
import mindustry.game.EventType;
import arc.Core;
import arc.util.Log;
import arc.util.Http;
import com.onex01.cloudsave.ui.CloudSaveDialog;
import com.onex01.cloudsave.util.ConfigManager;
import mindustry.Vars;
import mindustry.gen.Icon;

public class CloudSaveMod extends Mod {
    
    private static CloudSaveMod instance;
    private CloudSaveDialog cloudSaveDialog;
    private ConfigManager configManager;
    
    public CloudSaveMod() {
        instance = this;
        Log.info("[CloudSave] Мод создан!");
    }
    
    @Override
    public void init() {
        Log.info("[CloudSave] Инициализация мода...");
        
        // Загружаем конфигурацию
        configManager = new ConfigManager();
        
        // Подписываемся на событие загрузки клиента
        arc.Events.on(EventType.ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                try {
                    // Создаём диалог
                    cloudSaveDialog = new CloudSaveDialog();
                    
                    // Добавляем кнопку в главное меню
                    // Используем правильный API из реального мода MindustryToolMod
                    Vars.ui.menufrag.addButton("☁️ Cloud Saves", Icon.refresh, () -> {
                        cloudSaveDialog.show();
                    });
                    
                    Log.info("[CloudSave] Кнопка добавлена в главное меню!");
                } catch (Exception err) {
                    Log.err("[CloudSave] Ошибка при инициализации UI: " + err.getMessage());
                    err.printStackTrace();
                }
            });
        });
        
        Log.info("[CloudSave] Мод успешно загружен!");
    }
    
    @Override
    public void loadContent() {
        // Загрузка контента (не требуется для нашего мода)
    }
    
    public static CloudSaveMod getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public CloudSaveDialog getCloudSaveDialog() {
        return cloudSaveDialog;
    }
}