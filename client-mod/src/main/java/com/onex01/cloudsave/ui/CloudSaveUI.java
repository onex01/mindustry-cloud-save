package com.onex01.cloudsave.ui;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.onex01.cloudsave.CloudSaveMod;
import com.onex01.cloudsave.network.ApiClient;
import mindustry.Vars;
import mindustry.gen.Icon;

import java.io.File;

public class CloudSaveUI {
    
    private final ApiClient apiClient;
    private final Gson gson;
    
    public CloudSaveUI() {
        this.apiClient = new ApiClient(CloudSaveMod.getInstance().getConfigManager());
        this.gson = new Gson();
    }
    
    /**
     * Показывает главное окно облачных сохранений
     */
    public void show() {
        if (!CloudSaveMod.getInstance().getConfigManager().isLoggedIn()) {
            // Если не авторизован, показываем окно входа
            showLoginDialog();
        } else {
            // Если авторизован, показываем список сохранений
            showSavesList();
        }
    }
    
    /**
     * Показывает диалог входа/регистрации
     */
    private void showLoginDialog() {
        LoginDialog dialog = new LoginDialog(apiClient);
        dialog.setOnLoginSuccess(() -> {
            dialog.hide();
            showSavesList();
        });
        dialog.show();
    }
    
    /**
     * Показывает список облачных сохранений
     */
    private void showSavesList() {
        Dialog dialog = new Dialog("☁️ Cloud Saves");
        
        Table content = new Table();
        content.defaults().pad(10);
        
        // Заголовок с информацией о пользователе
        String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        content.add("Пользователь: " + username).row();
        content.add().height(10).row();
        
        // Кнопки действий
        Table buttons = new Table();
        buttons.defaults().size(150, 50).pad(5);
        
        buttons.button("Загрузить в облако", Icon.upOpen, () -> {
            uploadCurrentSave(dialog);
        });
        
        buttons.button("Обновить список", Icon.refresh, () -> {
            dialog.hide();
            showSavesList();
        });
        
        buttons.button("Выйти", Icon.exit, () -> {
            CloudSaveMod.getInstance().getConfigManager().logout();
            dialog.hide();
        });
        
        content.add(buttons).row();
        content.add().height(10).row();
        
        // Загрузка списка сохранений
        content.add("Загрузка списка сохранений...").name("status").row();
        
        dialog.cont.add(content).pad(20);
        
        dialog.buttons.defaults().size(150, 50);
        dialog.buttons.button("Закрыть", dialog::hide);
        
        dialog.show();
        
        // Загружаем список сохранений
        loadSavesList(content);
    }
    
    /**
     * Загружает список сохранений с сервера
     */
    private void loadSavesList(Table content) {
        apiClient.getSaves(new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Core.app.post(() -> {
                    try {
                        JsonObject json = gson.fromJson(response, JsonObject.class);
                        JsonArray saves = json.getAsJsonArray("saves");
                        
                        // Очищаем статус
                        content.getCell(content.findActor("status")).setActor(null);
                        
                        if (saves.size() == 0) {
                            content.add("Нет облачных сохранений").row();
                            return;
                        }
                        
                        // Создаем таблицу для списка
                        Table savesTable = new Table();
                        savesTable.defaults().pad(5);
                        
                        for (int i = 0; i < saves.size(); i++) {
                            JsonObject save = saves.get(i).getAsJsonObject();
                            int id = save.get("id").getAsInt();
                            String name = save.get("name").getAsString();
                            String date = save.get("created_at").getAsString();
                            
                            Table row = new Table();
                            row.defaults().pad(5);
                            
                            row.add(name).left().width(200);
                            row.add(date.substring(0, 10)).left().width(100);
                            
                            row.button("Скачать", Icon.download, () -> {
                                downloadSave(id, name);
                            }).size(100, 40);
                            
                            savesTable.add(row).row();
                        }
                        
                        content.add(savesTable).row();
                        
                    } catch (Exception e) {
                        Log.err("[CloudSave] Ошибка парсинга списка: " + e.getMessage());
                        content.getCell(content.findActor("status")).setActor(new Label("Ошибка загрузки"));
                    }
                });
            }
            
            @Override
            public void onFailure(String error) {
                Core.app.post(() -> {
                    content.getCell(content.findActor("status")).setActor(new Label("Ошибка: " + error));
                });
            }
        });
    }
    
    /**
     * Загружает текущее сохранение в облако
     */
    private void uploadCurrentSave(Dialog parentDialog) {
        // Получаем последнее сохранение
        File savesDir = Vars.saveDirectory.file();
        File[] saveFiles = savesDir.listFiles((dir, name) -> name.endsWith(".msav"));
        
        if (saveFiles == null || saveFiles.length == 0) {
            new Dialog("Ошибка").text("Нет сохранений для загрузки").buttons().button("OK", parentDialog::hide).show();
            return;
        }
        
        // Берем самое новое сохранение
        File latestSave = saveFiles[0];
        for (File file : saveFiles) {
            if (file.lastModified() > latestSave.lastModified()) {
                latestSave = file;
            }
        }
        
        String saveName = latestSave.getName().replace(".msav", "");
        
        apiClient.uploadSave(latestSave, saveName, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Core.app.post(() -> {
                    new Dialog("Успех").text("Сохранение загружено в облако!")
                        .buttons().button("OK", () -> {
                            parentDialog.hide();
                            showSavesList();
                        }).show();
                });
            }
            
            @Override
            public void onFailure(String error) {
                Core.app.post(() -> {
                    new Dialog("Ошибка").text("Ошибка загрузки: " + error)
                        .buttons().button("OK", () -> {}).show();
                });
            }
        });
    }
    
    /**
     * Скачивает сохранение из облака
     */
    private void downloadSave(int saveId, String saveName) {
        File savesDir = Vars.saveDirectory.file();
        File destinationFile = new File(savesDir, "cloud_" + saveName + ".msav");
        
        apiClient.downloadSave(saveId, destinationFile, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Core.app.post(() -> {
                    new Dialog("Успех").text("Сохранение скачано!\nПерезапустите игру для применения.")
                        .buttons().button("OK", () -> {}).show();
                });
            }
            
            @Override
            public void onFailure(String error) {
                Core.app.post(() -> {
                    new Dialog("Ошибка").text("Ошибка скачивания: " + error)
                        .buttons().button("OK", () -> {}).show();
                });
            }
        });
    }
}