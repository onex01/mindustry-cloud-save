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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

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
        
        loginTable.add(Core.bundle.get("mod.cloudsave.server")).left().row();
        serverField = new TextField(CloudSaveMod.getInstance().getConfigManager().getServerUrl());
        serverField.setMessageText("http://localhost:3000");
        loginTable.add(serverField).width(400).row();
        
        loginTable.add().height(10).row();
        
        loginTable.add(Core.bundle.get("mod.cloudsave.username")).left().row();
        usernameField = new TextField("");
        usernameField.setMessageText(Core.bundle.get("mod.cloudsave.username.placeholder"));
        loginTable.add(usernameField).width(400).row();
        
        loginTable.add(Core.bundle.get("mod.cloudsave.password")).left().row();
        passwordField = new TextField("");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('•');
        passwordField.setMessageText(Core.bundle.get("mod.cloudsave.password.placeholder"));
        loginTable.add(passwordField).width(400).row();
        
        loginTable.add().height(20).row();
        
        statusLabel = new TextField("");
        statusLabel.setDisabled(true);
        loginTable.add(statusLabel).width(400).row();
        
        cont.add(loginTable).pad(20);
        
        buttons.defaults().size(150, 50).pad(5);
        
        buttons.button("Тест соединения", () -> {
            testConnection();
        }).row();
        
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
        
        savesTable = new Table();
        savesTable.top().left();
        
        cont.add(savesTable).grow().row();
        
        loadSavesList();
    }
    
    private void testConnection() {
        String serverUrl = serverField.getText();
        setStatus("Тест соединения...");
        
        Log.info("[CloudSave] Тест: начинаем проверку с " + serverUrl);
        
        new Thread(() -> {
            try {
                Log.info("[CloudSave] Тест: создаем URL connection");
                
                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                Log.info("[CloudSave] Тест: отправляем запрос...");
                
                int responseCode = conn.getResponseCode();
                Log.info("[CloudSave] Тест: получен ответ " + responseCode);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                String body = response.toString();
                Log.info("[CloudSave] Тест: тело ответа: " + body);
                
                Core.app.post(() -> {
                    setStatus("Ответ сервера: " + responseCode + " - " + body);
                });
                
            } catch (Throwable e) {
                Log.err("[CloudSave] Тест: ОШИБКА: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    setStatus("Ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                });
            }
        }).start();
        
        Log.info("[CloudSave] Тест: поток запущен");
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
        Log.info("[CloudSave] JSON: " + json);
        
        new Thread(() -> {
            try {
                Log.info("[CloudSave] Поток регистрации запущен");
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                Log.info("[CloudSave] Отправка запроса...");
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
                int responseCode = conn.getResponseCode();
                Log.info("[CloudSave] Ответ сервера: " + responseCode);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                String responseBody = response.toString();
                Log.info("[CloudSave] Тело ответа: " + responseBody);
                
                Core.app.post(() -> {
                    try {
                        if (responseCode == 201 || responseCode == 200) {
                            setStatus(Core.bundle.get("mod.cloudsave.success.register"));
                        } else {
                            setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + responseBody);
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
        
        Log.info("[CloudSave] Поток регистрации запущен");
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
        Log.info("[CloudSave] JSON: " + json);
        
        new Thread(() -> {
            try {
                Log.info("[CloudSave] Поток входа запущен");
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                Log.info("[CloudSave] Отправка запроса...");
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
                int responseCode = conn.getResponseCode();
                Log.info("[CloudSave] Ответ сервера: " + responseCode);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                String responseBody = response.toString();
                Log.info("[CloudSave] Тело ответа: " + responseBody);
                
                Core.app.post(() -> {
                    try {
                        if (responseCode == 200) {
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
                            setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + responseBody);
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
        
        Log.info("[CloudSave] Поток входа запущен");
    }
    
    private void loadSavesList() {
        savesTable.clear();
        savesTable.add(Core.bundle.get("mod.cloudsave.status.loading")).pad(20);
        
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                String responseBody = response.toString();
                
                Core.app.post(() -> {
                    try {
                        Jval responseJson = Jval.read(responseBody);
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
        
        if (!savesDir.exists() || !savesDir.isDirectory()) {
            Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error.nosaves"));
            return;
        }
        
        final String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        final String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        final String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        
        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.status.uploading"));
        
        new Thread(() -> {
            try {
                Log.info("[CloudSave] Создание ZIP архива папки: " + savesDir.getAbsolutePath());
                
                // Создаем временный ZIP файл
                File tempZip = File.createTempFile("mindustry-saves-", ".zip");
                tempZip.deleteOnExit();
                
                // Архивируем всю папку saves
                try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                        new FileOutputStream(tempZip))) {
                    
                    archiveFolder(savesDir, savesDir.getName(), zos);
                }
                
                Log.info("[CloudSave] ZIP создан, размер: " + tempZip.length() + " байт");
                
                // Читаем ZIP в base64
                byte[] zipBytes = java.nio.file.Files.readAllBytes(tempZip.toPath());
                String base64Content = java.util.Base64.getEncoder().encodeToString(zipBytes);
                
                Log.info("[CloudSave] Base64 размер: " + base64Content.length() + " символов");
                
                // Отправляем на сервер
                String json = "{\"name\":\"full-save-" + username + "\"," +
                            "\"filename\":\"saves.zip\"," +
                            "\"content\":\"" + base64Content + "\"}";
                
                Log.info("[CloudSave] Отправка ZIP на сервер...");
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(60000); // 60 секунд
                conn.setReadTimeout(60000);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
                int responseCode = conn.getResponseCode();
                Log.info("[CloudSave] Ответ сервера: " + responseCode);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                Log.info("[CloudSave] Тело ответа: " + response.toString());
                
                Core.app.post(() -> {
                    if (responseCode == 201 || responseCode == 200) {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.success.upload"));
                        loadSavesList();
                    } else {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": HTTP " + responseCode + " - " + response.toString());
                    }
                });
                
                // Удаляем временный файл
                tempZip.delete();
                
            } catch (Exception e) {
                Log.err("[CloudSave] Ошибка загрузки: " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }

    // Вспомогательный метод для архивации папки
    private void archiveFolder(File folder, String parentPath, java.util.zip.ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            String entryName = parentPath + "/" + file.getName();
            
            if (file.isDirectory()) {
                archiveFolder(file, entryName, zos);
            } else {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                zos.putNextEntry(entry);
                
                java.nio.file.Files.copy(file.toPath(), zos);
                
                zos.closeEntry();
            }
        }
    }
    
    private void downloadSave(int saveId, String saveName) {
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves/" + saveId + "/download";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.status.downloading"));
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    // Сохраняем ZIP во временный файл
                    File tempZip = File.createTempFile("mindustry-download-", ".zip");
                    
                    try (InputStream is = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(tempZip)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    Log.info("[CloudSave] ZIP скачан, размер: " + tempZip.length() + " байт");
                    
                    // Распаковываем в папку saves
                    File savesDir = Vars.saveDirectory.file();
                    extractZip(tempZip, savesDir);
                    
                    // Удаляем временный файл
                    tempZip.delete();
                    
                    Core.app.post(() -> {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.success.download"));
                    });
                } else {
                    Core.app.post(() -> {
                        Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": HTTP " + responseCode);
                    });
                }
                
                conn.disconnect();
                
            } catch (Exception e) {
                Log.err("[CloudSave] Ошибка скачивания: " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    Vars.ui.showInfo(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }

    // Вспомогательный метод для распаковки ZIP
    private void extractZip(File zipFile, File destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir.getParentFile(), entry.getName());
                
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
    
    private void setStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }
}