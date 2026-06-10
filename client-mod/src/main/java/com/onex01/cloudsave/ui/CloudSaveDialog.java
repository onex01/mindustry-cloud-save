package com.onex01.cloudsave.ui;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Http;
import arc.util.Log;
import arc.util.Timer;
import arc.util.serialization.Jval;
import com.onex01.cloudsave.CloudSaveMod;
import mindustry.Vars;
import mindustry.gen.Icon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CloudSaveDialog extends Dialog {
    
    private TextField serverField;
    private TextField usernameField;
    private TextField passwordField;
    private Table savesTable;
    private TextField statusLabel;
    
    public CloudSaveDialog() {
        super("☁️ Cloud Saves");
        
        addCloseButton();
        closeOnBack();
        
        shown(this::setupUI);
    }
    
    private void setupUI() {
        cont.clear();
        cont.margin(10f);
        
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
        loginTable.add("Сервер API:").left().row();
        serverField = new TextField(CloudSaveMod.getInstance().getConfigManager().getServerUrl());
        serverField.setMessageText("http://localhost:3000");
        loginTable.add(serverField).width(400).row();
        
        loginTable.add().height(10).row();
        
        // Поле для логина
        loginTable.add("Имя пользователя:").left().row();
        usernameField = new TextField("");
        usernameField.setMessageText("Введите логин");
        loginTable.add(usernameField).width(400).row();
        
        // Поле для пароля
        loginTable.add("Пароль:").left().row();
        passwordField = new TextField("");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('•');
        passwordField.setMessageText("Введите пароль");
        loginTable.add(passwordField).width(400).row();
        
        loginTable.add().height(20).row();
        
        // Статус
        statusLabel = new TextField("");
        statusLabel.setDisabled(true);
        loginTable.add(statusLabel).width(400).row();
        
        cont.add(loginTable).pad(20);
        
        // Кнопки
        buttons.defaults().size(150, 50).pad(5);
        
        buttons.button("Регистрация", () -> {
            register();
        });
        
        buttons.button("Вход", () -> {
            login();
        });
    }
    
    private void showSavesUI() {
        cont.clear();
        
        String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        
        Table headerTable = new Table();
        headerTable.add("Пользователь: " + username).left().pad(10);
        headerTable.button("Выйти", () -> {
            CloudSaveMod.getInstance().getConfigManager().logout();
            setupUI();
        }).size(100, 40).right().pad(10);
        
        cont.add(headerTable).growX().row();
        cont.add().height(10).row();
        
        // Кнопки действий
        Table actionsTable = new Table();
        actionsTable.defaults().size(150, 50).pad(5);
        
        actionsTable.button("Загрузить в облако", Icon.upOpen, () -> {
            uploadCurrentSave();
        });
        
        actionsTable.button("Обновить", Icon.refresh, () -> {
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
            setStatus("Заполните все поля");
            return;
        }
        
        setStatus("Регистрация...");
        
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        String url = serverUrl + "/api/auth/register";
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        
        Log.info("[CloudSave] Регистрация: " + url);
        
        // Запускаем в отдельном потоке, чтобы не блокировать UI
        new Thread(() -> {
            try {
                // Используем стандартный Java HTTP клиент
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                
                Log.info("[CloudSave] Ответ сервера: " + response.statusCode() + " - " + response.body());
                
                Core.app.post(() -> {
                    try {
                        if (response.statusCode() == 201 || response.statusCode() == 200) {
                            setStatus("Регистрация успешна! Теперь войдите.");
                        } else {
                            setStatus("Ошибка: " + response.body());
                        }
                    } catch (Exception e) {
                        setStatus("Ошибка: " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Log.err("[CloudSave] Ошибка регистрации: " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    setStatus("Ошибка: " + e.getMessage());
                });
            }
        }).start();
    }

    private void login() {
        String serverUrl = serverField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Заполните все поля");
            return;
        }
        
        setStatus("Вход...");
        
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        String url = serverUrl + "/api/auth/login";
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        
        Log.info("[CloudSave] Вход: " + url);
        
        // Запускаем в отдельном потоке
        new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                
                Log.info("[CloudSave] Ответ сервера: " + response.statusCode() + " - " + response.body());
                
                Core.app.post(() -> {
                    try {
                        if (response.statusCode() == 200) {
                            // Парсим JSON ответ
                            String responseBody = response.body();
                            Jval responseJson = Jval.read(responseBody);
                            
                            if (responseJson.has("token")) {
                                String token = responseJson.getString("token");
                                CloudSaveMod.getInstance().getConfigManager().setAuthToken(token);
                                CloudSaveMod.getInstance().getConfigManager().setUsername(username);
                                
                                setStatus("Успешный вход!");
                                
                                Timer.schedule(() -> {
                                    Core.app.post(() -> {
                                        setupUI();
                                    });
                                }, 0.5f);
                            } else {
                                setStatus("Ошибка: токен не получен");
                            }
                        } else {
                            setStatus("Ошибка: " + response.body());
                        }
                    } catch (Exception e) {
                        Log.err("[CloudSave] Ошибка парсинга: " + e.getMessage());
                        setStatus("Ошибка: " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Log.err("[CloudSave] Ошибка входа: " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    setStatus("Ошибка: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void loadSavesList() {
        savesTable.clear();
        savesTable.add("Загрузка...").pad(20);
        
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                
                Core.app.post(() -> {
                    try {
                        Jval responseJson = Jval.read(response.body());
                        Jval saves = responseJson.get("saves");
                        
                        savesTable.clear();
                        
                        if (saves == null || saves.asArray().size == 0) {
                            savesTable.add("Нет облачных сохранений").pad(20);
                            return;
                        }
                        
                        for (Jval save : saves.asArray()) {
                            int id = save.getInt("id", 0);
                            String name = save.getString("name", "Без названия");
                            String date = save.getString("created_at", "");
                            
                            Table row = new Table();
                            row.defaults().pad(5);
                            
                            row.add(name).left().width(200);
                            row.add(date.length() >= 10 ? date.substring(0, 10) : date).left().width(100);
                            
                            row.button("Скачать", Icon.download, () -> {
                                downloadSave(id, name);
                            }).size(100, 40);
                            
                            savesTable.add(row).growX().row();
                        }
                        
                    } catch (Exception e) {
                        savesTable.clear();
                        savesTable.add("Ошибка: " + e.getMessage()).pad(20);
                    }
                });
                
            } catch (Exception e) {
                Core.app.post(() -> {
                    savesTable.clear();
                    savesTable.add("Ошибка: " + e.getMessage()).pad(20);
                });
            }
        }).start();
    }
    
    private void uploadCurrentSave() {
        File savesDir = Vars.saveDirectory.file();
        File[] saveFiles = savesDir.listFiles((dir, name) -> name.endsWith(".msav"));
        
        if (saveFiles == null || saveFiles.length == 0) {
            Vars.ui.showInfo("Нет сохранений для загрузки");
            return;
        }
        
        File latestSave = saveFiles[0];
        for (File file : saveFiles) {
            if (file.lastModified() > latestSave.lastModified()) {
                latestSave = file;
            }
        }
        
        String saveName = latestSave.getName().replace(".msav", "");
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        Vars.ui.showInfo("Загрузка сохранения...");
        
        try {
            byte[] fileBytes = java.nio.file.Files.readAllBytes(latestSave.toPath());
            
            // Кодируем файл в Base64
            String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);
            
            // Формируем JSON с base64 контентом
            String json = "{\"name\":\"" + saveName + "\"," +
                        "\"filename\":\"" + latestSave.getName() + "\"," +
                        "\"content\":\"" + base64Content + "\"}";
            
            Http.post(url)
                .content(json)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .error(error -> {
                    Core.app.post(() -> {
                        Vars.ui.showInfo("Ошибка загрузки: " + error.getMessage());
                    });
                })
                .submit(result -> {
                    Core.app.post(() -> {
                        Vars.ui.showInfo("Сохранение загружено в облако!");
                        loadSavesList();
                    });
                });
            
        } catch (IOException e) {
            Vars.ui.showInfo("Ошибка чтения файла: " + e.getMessage());
        }
    }
    
    private void downloadSave(int saveId, String saveName) {
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves/" + saveId + "/download";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        Vars.ui.showInfo("Скачивание сохранения...");
        
        Http.get(url)
            .header("Authorization", "Bearer " + token)
            .error(error -> {
                Core.app.post(() -> {
                    Vars.ui.showInfo("Ошибка скачивания: " + error.getMessage());
                });
            })
            .submit(result -> {
                Core.app.post(() -> {
                    try {
                        File savesDir = Vars.saveDirectory.file();
                        File destinationFile = new File(savesDir, "cloud_" + saveName + ".msav");
                        
                        // result.getResult() возвращает byte[]
                        byte[] fileData = result.getResult();
                        
                        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                            fos.write(fileData);
                        }
                        
                        Vars.ui.showInfo("Сохранение скачано!\nПерезапустите игру для применения.");
                    } catch (Exception e) {
                        Vars.ui.showInfo("Ошибка сохранения: " + e.getMessage());
                    }
                });
            });
    }
    
    private void setStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }
}