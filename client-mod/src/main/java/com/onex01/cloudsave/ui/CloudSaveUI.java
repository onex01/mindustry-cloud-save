package com.onex01.cloudsave.ui;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
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
    private Label statusLabel; // Сохраняем ссылку на label статуса
    
    public CloudSaveUI() {
        this.apiClient = new ApiClient(CloudSaveMod.getInstance().getConfigManager());
        this.gson = new Gson();
    }
    
    public void show() {
        if (!CloudSaveMod.getInstance().getConfigManager().isLoggedIn()) {
            showLoginDialog();
        } else {
            showSavesList();
        }
    }
    
    private void showLoginDialog() {
        LoginDialog dialog = new LoginDialog(apiClient);
        dialog.setOnLoginSuccess(() -> {
            dialog.hide();
            showSavesList();
        });
        dialog.show();
    }
    
    private void showSavesList() {
        Dialog dialog = new Dialog("☁️ Cloud Saves");
        
        Table content = new Table();
        content.defaults().pad(10);
        
        String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        content.add("Пользователь: " + username).row();
        content.add().height(10).row();
        
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
        
        // Сохраняем ссылку на label статуса
        statusLabel = new Label("Загрузка списка сохранений...");
        content.add(statusLabel).row();
        
        dialog.cont.add(content).pad(20);
        
        // Используем поле buttons напрямую (не метод)
        dialog.buttons.defaults().size(150, 50);
        dialog.buttons.button("Закрыть", dialog::hide);
        
        dialog.show();
        
        loadSavesList(content);
    }
    
    private void loadSavesList(Table content) {
        apiClient.getSaves(new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Core.app.post(() -> {
                    try {
                        JsonObject json = gson.fromJson(response, JsonObject.class);
                        JsonArray saves = json.getAsJsonArray("saves");
                        
                        // Очищаем статус через label
                        if (statusLabel != null) {
                            statusLabel.setText("");
                        }
                        
                        if (saves.size() == 0) {
                            content.add("Нет облачных сохранений").row();
                            return;
                        }
                        
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
                        if (statusLabel != null) {
                            statusLabel.setText("Ошибка загрузки");
                        }
                    }
                });
            }
            
            @Override
            public void onFailure(String error) {
                Core.app.post(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("Ошибка: " + error);
                    }
                });
            }
        });
    }
    
    private void uploadCurrentSave(Dialog parentDialog) {
        File savesDir = Vars.saveDirectory.file();
        File[] saveFiles = savesDir.listFiles((dir, name) -> name.endsWith(".msav"));
        
        if (saveFiles == null || saveFiles.length == 0) {
            Dialog errorDialog = new Dialog("Ошибка");
            errorDialog.cont.add("Нет сохранений для загрузки");
            errorDialog.buttons.button("OK", parentDialog::hide);
            errorDialog.show();
            return;
        }
        
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
                    Dialog successDialog = new Dialog("Успех");
                    successDialog.cont.add("Сохранение загружено в облако!");
                    successDialog.buttons.button("OK", () -> {
                        parentDialog.hide();
                        showSavesList();
                    });
                    successDialog.show();
                });
            }
            
            @Override
            public void onFailure(String error) {
                Core.app.post(() -> {
                    Dialog errorDialog = new Dialog("Ошибка");
                    errorDialog.cont.add("Ошибка загрузки: " + error);
                    errorDialog.buttons.button("OK", () -> {});
                    errorDialog.show();
                });
            }
        });
    }
    
    private void downloadSave(int saveId, String saveName) {
        File savesDir = Vars.saveDirectory.file();
        File destinationFile = new File(savesDir, "cloud_" + saveName + ".msav");
        
        apiClient.downloadSave(saveId, destinationFile, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Core.app.post(() -> {
                    Dialog successDialog = new Dialog("Успех");
                    successDialog.cont.add("Сохранение скачано!\nПерезапустите игру для применения.");
                    successDialog.buttons.button("OK", () -> {});
                    successDialog.show();
                });
            }
            
            @Override
            public void onFailure(String error) {
                Core.app.post(() -> {
                    Dialog errorDialog = new Dialog("Ошибка");
                    errorDialog.cont.add("Ошибка скачивания: " + error);
                    errorDialog.buttons.button("OK", () -> {});
                    errorDialog.show();
                });
            }
        });
    }
}