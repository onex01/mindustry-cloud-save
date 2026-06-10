package com.onex01.cloudsave.ui;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Timer;
import arc.util.serialization.Jval;
import com.onex01.cloudsave.CloudSaveMod;
import mindustry.Vars;
import mindustry.gen.Icon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CloudSaveDialog extends Dialog {
    
    private TextField serverField;
    private TextField usernameField;
    private TextField passwordField;
    private Table savesTable;
    private TextField statusLabel;
    
    public CloudSaveDialog() {
        super(Core.bundle.get("mod.cloudsave.title"));
        
        addCloseButton();
        closeOnBack();
        
        shown(this::setupUI);
    }
    
    private void setupUI() {
        cont.clear();
        cont.margin(10f);
        
        // ВАЖНО: Очищаем кнопки перед добавлением новых!
        buttons.clear();
        
        if (!CloudSaveMod.getInstance().getConfigManager().isLoggedIn()) {
            showLoginUI();
        } else {
            showSavesUI();
        }
    }
    
    private void showLoginUI() {
        cont.clear();
        
        Table loginTable = new Table();
        loginTable.defaults().pad(10);
        
        // Поле для сервера
        loginTable.add(Core.bundle.get("mod.cloudsave.server")).left().row();
        serverField = new TextField(CloudSaveMod.getInstance().getConfigManager().getServerUrl());
        serverField.setMessageText("http://localhost:3000");
        loginTable.add(serverField).width(400).row();
        
        loginTable.add().height(10).row();
        
        // Поле для логина
        loginTable.add(Core.bundle.get("mod.cloudsave.username")).left().row();
        usernameField = new TextField("");
        usernameField.setMessageText(Core.bundle.get("mod.cloudsave.username.placeholder"));
        loginTable.add(usernameField).width(400).row();
        
        // Поле для пароля
        loginTable.add(Core.bundle.get("mod.cloudsave.password")).left().row();
        passwordField = new TextField("");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('•');
        passwordField.setMessageText(Core.bundle.get("mod.cloudsave.password.placeholder"));
        loginTable.add(passwordField).width(400).row();
        
        loginTable.add().height(20).row();
        
        // Статус
        statusLabel = new TextField("");
        statusLabel.setDisabled(true);
        loginTable.add(statusLabel).width(400).row();
        
        cont.add(loginTable).pad(20);
        
        // Кнопки
        buttons.defaults().size(150, 50).pad(5);
        
        buttons.button(Core.bundle.get("mod.cloudsave.register"), () -> {
            register();
        });
        
        buttons.button(Core.bundle.get("mod.cloudsave.login"), () -> {
            login();
        });
    }
    
    private void showSavesUI() {
        cont.clear();
        
        String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        
        Table headerTable = new Table();
        headerTable.add(Core.bundle.get("mod.cloudsave.user") + ": " + username).left().pad(10);
        headerTable.button(Core.bundle.get("mod.cloudsave.logout"), () -> {
            CloudSaveMod.getInstance().getConfigManager().logout();
            setupUI();
        }).size(100, 40).right().pad(10);
        
        cont.add(headerTable).growX().row();
        cont.add().height(10).row();
        
        // Кнопки действий
        Table actionsTable = new Table();
        actionsTable.defaults().size(150, 50).pad(5);
        
        actionsTable.button(Core.bundle.get("mod.cloudsave.upload"), Icon.upOpen, () -> {
            uploadCurrentSave();
        });
        
        actionsTable.button(Core.bundle.get("mod.cloudsave.refresh"), Icon.refresh, () -> {
            loadSavesList();
        });
        
        cont.add(actionsTable).row();
        cont.add().height(10).row();
        
        // Таблица для списка сохранений
        savesTable = new Table();
        savesTable.top().left();
        
        cont.add(savesTable).grow().row();
        
        // Загружаем список сохранений
        loadSavesList();
    }
    
    private void register() {
        String serverUrl = serverField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            setStatus(Core.bundle.get("mod.cloudsave.error.fields"));
            return;
        }
        
        setStatus(Core.bundle.get("mod.cloudsave.status.registering"));
        
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        String url = serverUrl + "/api/auth/register";
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        
        Log.info("[CloudSave] Регистрация: " + url);
        
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();
                
                HttpResponse<String> response = client.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                Log.info("[CloudSave] Ответ сервера: " + response.statusCode() + " - " + response.body());
                
                Core.app.post(() -> {
                    try {
                        if (response.statusCode() == 201 || response.statusCode() == 200) {
                            setStatus(Core.bundle.get("mod.cloudsave.success.register"));
                        } else {
                            setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + response.body());
                        }
                    } catch (Exception e) {
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Log.err("[CloudSave] Ошибка регистрации: " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void login() {
        String serverUrl = serverField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            setStatus(Core.bundle.get("mod.cloudsave.error.fields"));
            return;
        }
        
        setStatus(Core.bundle.get("mod.cloudsave.status.logging"));
        
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        String url = serverUrl + "/api/auth/login";
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        
        Log.info("[CloudSave] Вход: " + url);
        
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();
                
                HttpResponse<String> response = client.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                Log.info("[CloudSave] Ответ сервера: " + response.statusCode() + " - " + response.body());
                
                Core.app.post(() -> {
                    try {
                        if (response.statusCode() == 200) {
                            String responseBody = response.body();
                            Jval responseJson = Jval.read(responseBody);
                            
                            if (responseJson.has("token")) {
                                String token = responseJson.getString("token");
                                CloudSaveMod.getInstance().getConfigManager().setAuthToken(token);
                                CloudSaveMod.getInstance().getConfigManager().setUsername(username);
                                
                                setStatus(Core.bundle.get("mod.cloudsave.success.login"));
                                
                                Timer.schedule(() -> {
                                    Core.app.post(() -> {
                                        setupUI();
                                    });
                                }, 0.5f);
                            } else {
                                setStatus(Core.bundle.get("mod.cloudsave.error.token"));
                            }
                        } else {
                            setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + response.body());
                        }
                    } catch (Exception e) {
                        Log.err("[CloudSave] Ошибка парсинга: " + e.getMessage());
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Log.err("[CloudSave] Ошибка входа: " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void loadSavesList() {
        savesTable.clear();
        savesTable.add(Core.bundle.get("mod.cloudsave.status.loading")).pad(20);
        
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
                
                HttpResponse<String> response = client.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                Core.app.post(() -> {
                    try {
                        Jval responseJson = Jval.read(response.body());
                        Jval saves = responseJson.get("saves");
                        
                        savesTable.clear();
                        
                        if (saves == null || saves.asArray().size == 0) {
                            savesTable.add(Core.bundle.get("mod.cloudsave.saves.empty")).pad(20);
                            return;
                        }
                        
                        for (Jval save : saves.asArray()) {
                            int id = save.getInt("id", 0);
                            String name = save.getString("name", Core.bundle.get("mod.cloudsave.saves.unnamed"));
                            String date = save.getString("created_at", "");
                            
                            Table row = new Table();
                            row.defaults().pad(5);
                            
                            row.add(name).left().width(200);
                            row.add(date.length() >= 10 ? date.substring(0, 10) : date).left().width(100);
                            
                            row.button(Core.bundle.get("mod.cloudsave.download"), Icon.download, () -> {
                                downloadSave(id, name);
                            }).size(100, 40);
                            
                            savesTable.add(row).growX().row();
                        }
                        
                    } catch (Exception e) {
                        savesTable.clear();
                        savesTable.add(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage()).pad(20);
                    }
                });
                
            } catch (Exception e) {
                Core.app.post(() -> {
                    savesTable.clear();
                    savesTable.add(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage()).pad(20);
                });
            }
        }).start();
    }
    
    private void uploadCurrentSave() {
        File savesDir = Vars.saveDirectory.file();
        File[] saveFiles = savesDir.listFiles((dir, name) -> name.endsWith(".msav"));
        
        if (saveFiles == null || saveFiles.length == 0) {
            Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error.nosaves"));
            return;
        }
        
        File latestSave = saveFiles[0];
        for (File file : saveFiles) {
            if (file.lastModified() > latestSave.lastModified()) {
                latestSave = file;
            }
        }
        
        // Создаём final-копии для использования в лямбде
        final File finalSave = latestSave;
        final String saveName = latestSave.getName().replace(".msav", "");
        final String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        final String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.status.uploading"));
        
        new Thread(() -> {
            try {
                byte[] fileBytes = java.nio.file.Files.readAllBytes(finalSave.toPath());
                String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);
                
                String json = "{\"name\":\"" + saveName + "\"," +
                            "\"filename\":\"" + finalSave.getName() + "\"," +
                            "\"content\":\"" + base64Content + "\"}";
                
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                HttpResponse<String> response = client.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                Core.app.post(() -> {
                    if (response.statusCode() == 201 || response.statusCode() == 200) {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.success.upload"));
                        loadSavesList();
                    } else {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": " + response.body());
                    }
                });
                
            } catch (Exception e) {
                Core.app.post(() -> {
                    Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void downloadSave(int saveId, String saveName) {
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves/" + saveId + "/download";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.status.downloading"));
        
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                HttpResponse<byte[]> response = client.send(request, 
                    HttpResponse.BodyHandlers.ofByteArray());
                
                Core.app.post(() -> {
                    try {
                        File savesDir = Vars.saveDirectory.file();
                        File destinationFile = new File(savesDir, "cloud_" + saveName + ".msav");
                        
                        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                            fos.write(response.body());
                        }
                        
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.success.download"));
                    } catch (Exception e) {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Core.app.post(() -> {
                    Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void setStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }
}