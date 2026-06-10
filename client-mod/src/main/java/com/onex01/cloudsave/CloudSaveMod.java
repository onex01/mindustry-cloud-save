package com.onex01.cloudsave;

import mindustry.mod.Mod;
import mindustry.game.EventType;
import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Timer;
import com.onex01.cloudsave.ui.CloudSaveUI;
import com.onex01.cloudsave.util.ConfigManager;
import mindustry.Vars;

import java.lang.reflect.Field;

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
        
        configManager = new ConfigManager();
        cloudSaveUI = new CloudSaveUI();
        
        // Подписываемся на событие загрузки клиента
        arc.Events.on(EventType.ClientLoadEvent.class, e -> {
            Timer.schedule(this::addButtonToMainMenu, 1.0f);
        });
        
        Log.info("[CloudSave] Мод успешно загружен!");
    }
    
    @Override
    public void loadContent() {
        // Загрузка контента
    }
    
    private void addButtonToMainMenu() {
        try {
            if (Core.scene == null) {
                Log.warn("[CloudSave] Scene еще не загружена, повторная попытка через 2 секунды...");
                Timer.schedule(this::addButtonToMainMenu, 2.0f);
                return;
            }
            
            // Используем reflection для доступа к приватному полю container
            Table menuTable = findMenuTable();
            
            if (menuTable != null) {
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
    
    private Table findMenuTable() {
        try {
            // Используем reflection для доступа к приватному полю container
            Field containerField = Vars.ui.menufrag.getClass().getDeclaredField("container");
            containerField.setAccessible(true);
            return (Table) containerField.get(Vars.ui.menufrag);
        } catch (Exception e) {
            Log.err("[CloudSave] Не удалось найти меню через reflection: " + e.getMessage());
            
            // Альтернативный способ — ищем через scene
            try {
                for (arc.scene.Element elem : Core.scene.getRoot().getChildren()) {
                    if (elem instanceof Table) {
                        return (Table) elem;
                    }
                }
            } catch (Exception ex) {
                Log.err("[CloudSave] Альтернативный способ тоже не сработал: " + ex.getMessage());
            }
            
            return null;
        }
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