package com.onex01.cloudsave.ui;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.scene.ui.ScrollPane;
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
        
        closeOnBack();
        shown(this::setupUI);
    }
    
    private void setupUI() {
        cont.clear();
        cont.margin(10f);
        buttons.clear();
        
        buttons.button(Core.bundle.get("mod.cloudsave.close"), Icon.cancel, () -> {
            hide();
        }).size(120, 40);
        
        if (!CloudSaveMod.getInstance().getConfigManager().isLoggedIn()) {
            showLoginUI();
        } else {
            showSavesUI();
        }
    }
    
    private void showLoginUI() {
        cont.clear();
        
        Table loginTable = new Table();
        loginTable.defaults().pad(8);
        
        loginTable.add(Core.bundle.get("mod.cloudsave.server")).left().row();
        serverField = new TextField(CloudSaveMod.getInstance().getConfigManager().getServerUrl());
        serverField.setMessageText("onex01.ru");
        loginTable.add(serverField).width(400).row();
        
        loginTable.add().height(5).row();
        
        loginTable.add(Core.bundle.get("mod.cloudsave.username")).left().row();
        usernameField = new TextField("");
        usernameField.setMessageText(Core.bundle.get("mod.cloudsave.username.placeholder"));
        loginTable.add(usernameField).width(400).row();
        
        loginTable.add(Core.bundle.get("mod.cloudsave.password")).left().row();
        passwordField = new TextField("");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('\u2022');
        passwordField.setMessageText(Core.bundle.get("mod.cloudsave.password.placeholder"));
        loginTable.add(passwordField).width(400).row();
        
        loginTable.add().height(10).row();
        
        statusLabel = new TextField("");
        statusLabel.setDisabled(true);
        loginTable.add(statusLabel).width(400).row();
        
        cont.add(loginTable).pad(10);
        
        buttons.defaults().size(130, 40).pad(3);
        
        buttons.button(Core.bundle.get("mod.cloudsave.test"), () -> {
            testConnection();
        });
        
        buttons.button(Core.bundle.get("mod.cloudsave.register"), () -> {
            register();
        });
        
        buttons.button(Core.bundle.get("mod.cloudsave.login"), () -> {
            login();
        });
        
        buttons.row();
        
        buttons.button("Debug: " + (CloudSaveMod.getInstance().getConfigManager().isDebugMode() ? "ON" : "OFF"), () -> {
            boolean current = CloudSaveMod.getInstance().getConfigManager().isDebugMode();
            CloudSaveMod.getInstance().getConfigManager().setDebugMode(!current);
            setupUI();
        }).size(150, 35);
    }
    
    private void showSavesUI() {
        cont.clear();
        
        String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        
        Table headerTable = new Table();
        headerTable.add(Core.bundle.get("mod.cloudsave.user") + ": " + username).left().pad(5);
        headerTable.button(Core.bundle.get("mod.cloudsave.logout"), () -> {
            CloudSaveMod.getInstance().getConfigManager().logout();
            setupUI();
        }).size(100, 35).right().pad(5);
        
        cont.add(headerTable).growX().row();
        cont.add().height(5).row();
        
        Table actionsTable = new Table();
        actionsTable.defaults().size(130, 40).pad(3);
        
        actionsTable.button(Core.bundle.get("mod.cloudsave.upload"), Icon.upOpen, () -> {
            uploadCurrentSave();
        });
        
        actionsTable.button(Core.bundle.get("mod.cloudsave.refresh"), Icon.refresh, () -> {
            loadSavesList();
        });
        
        cont.add(actionsTable).row();
        cont.add().height(10).row();
        
        savesTable = new Table();
        savesTable.top();
        
        ScrollPane scrollPane = new ScrollPane(savesTable);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        cont.add(scrollPane).grow().row();
        
        loadSavesList();
    }
    
    private void testConnection() {
        String inputUrl = serverField.getText().trim();
        if (inputUrl.isEmpty()) {
            setStatus(Core.bundle.get("mod.cloudsave.error.fields"));
            return;
        }
        
        String serverUrl = resolveServerUrl(inputUrl);
        setStatus(Core.bundle.get("mod.cloudsave.status.testing"));
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        new Thread(() -> {
            String lastError = null;
            String[] urlsToTry;
            
            if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://")) {
                // Если указан порт — пробуем http, потом https
                // Если без порта — пробуем https, потом http
                if (inputUrl.contains(":")) {
                    urlsToTry = new String[] { "http://" + inputUrl, "https://" + inputUrl };
                } else {
                    urlsToTry = new String[] { "https://" + inputUrl, "http://" + inputUrl };
                }
            } else {
                urlsToTry = new String[] { inputUrl };
            }
            
            for (String url : urlsToTry) {
                try {
                    URL urlObj = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    int responseCode = conn.getResponseCode();
                    
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    conn.disconnect();
                    
                    CloudSaveMod.getInstance().getConfigManager().setServerUrl(url);
                    
                    final String finalUrl = url;
                    Core.app.post(() -> {
                        setStatus("OK: " + finalUrl);
                        setupUI();
                    });
                    return;
                    
                } catch (Throwable e) {
                    lastError = e.getMessage();
                    Log.err("[CloudSave] Test " + url + " failed: " + lastError);
                }
            }
            
            final String finalError = lastError;
            Core.app.post(() -> {
                if (finalError != null && finalError.contains("Cleartext HTTP")) {
                    setStatus("HTTP blocked. Use HTTPS");
                } else {
                    setStatus("Error: " + finalError);
                }
                setupUI();
            });
        }).start();
    }
    
    private void register() {
        String inputUrl = serverField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            setStatus(Core.bundle.get("mod.cloudsave.error.fields"));
            return;
        }
        
        String serverUrl = resolveServerUrl(inputUrl);
        setStatus(Core.bundle.get("mod.cloudsave.status.registering"));
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        String url = serverUrl + "/api/auth/register";
        String json = "{\"username\":\"" + escapeJson(username) + "\",\"password\":\"" + escapeJson(password) + "\"}";
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
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
                    if (responseCode == 201 || responseCode == 200) {
                        setStatus(Core.bundle.get("mod.cloudsave.success.register"));
                    } else {
                        String errMsg = parseError(responseBody);
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + errMsg);
                    }
                    setupUI();
                });
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Cleartext HTTP")) {
                    errorMsg = "HTTP blocked. Use HTTPS or 127.0.0.1";
                }
                final String finalMsg = errorMsg;
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + finalMsg);
                    setupUI();
                });
            }
        }).start();
    }
    
    private void login() {
        String inputUrl = serverField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            setStatus(Core.bundle.get("mod.cloudsave.error.fields"));
            return;
        }
        
        String serverUrl = resolveServerUrl(inputUrl);
        setStatus(Core.bundle.get("mod.cloudsave.status.logging"));
        CloudSaveMod.getInstance().getConfigManager().setServerUrl(serverUrl);
        
        String url = serverUrl + "/api/auth/login";
        String json = "{\"username\":\"" + escapeJson(username) + "\",\"password\":\"" + escapeJson(password) + "\"}";
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
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
                    if (responseCode == 200) {
                        try {
                            Jval responseJson = Jval.read(responseBody);
                            
                            if (responseJson.has("token")) {
                                String token = responseJson.getString("token");
                                CloudSaveMod.getInstance().getConfigManager().setAuthToken(token);
                                CloudSaveMod.getInstance().getConfigManager().setUsername(username);
                                
                                setStatus(Core.bundle.get("mod.cloudsave.success.login"));
                                setupUI();
                            } else {
                                setStatus(Core.bundle.get("mod.cloudsave.error.token"));
                                setupUI();
                            }
                        } catch (Exception e) {
                            setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                            setupUI();
                        }
                    } else {
                        String errMsg = parseError(responseBody);
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + errMsg);
                        setupUI();
                    }
                });
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Cleartext HTTP")) {
                    errorMsg = "HTTP blocked. Use HTTPS or 127.0.0.1";
                }
                final String finalMsg = errorMsg;
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + finalMsg);
                    setupUI();
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
                            String device = save.getString("device", "");
                            int fileSize = save.getInt("file_size", 0);
                            
                            String sizeStr = fileSize > 1024*1024 ? (fileSize/(1024*1024)) + "MB" : 
                                           fileSize > 1024 ? (fileSize/1024) + "KB" : fileSize + "B";
                            String dateStr = date.length() >= 16 ? date.substring(0, 16) : date;
                            
                            // Заголовок — только имя
                            Table item = new Table();
                            item.defaults().pad(2);
                            
                            item.button(name, () -> {
                                showSaveDetail(id, name, device, dateStr, sizeStr, item);
                            }).width(480).height(36).left().padLeft(10);
                            
                            savesTable.add(item).width(490).fillX().pad(2).row();
                        }
                        
                    } catch (Exception e) {
                        savesTable.clear();
                        savesTable.add(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage()).pad(20);
                    }
                });
                
            } catch (Exception e) {
                Core.app.post(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("Cleartext HTTP")) {
                        errorMsg = "HTTP blocked. Use HTTPS or 127.0.0.1";
                    }
                    savesTable.clear();
                    savesTable.add(Core.bundle.get("mod.cloudsave.error") + ": " + errorMsg).pad(20);
                });
            }
        }).start();
    }
    
    private void uploadCurrentSave() {
        File savesDir = Vars.saveDirectory.file();
        
        if (!savesDir.exists() || !savesDir.isDirectory()) {
            setStatus(Core.bundle.get("mod.cloudsave.error.nosaves"));
            setupUI();
            return;
        }
        
        final String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves";
        final String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        final String username = CloudSaveMod.getInstance().getConfigManager().getUsername();
        
        setStatus(Core.bundle.get("mod.cloudsave.status.uploading"));
        
        new Thread(() -> {
            try {
                File tempZip = File.createTempFile("mindustry-saves-", ".zip");
                tempZip.deleteOnExit();
                
                try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                        new FileOutputStream(tempZip))) {
                    archiveFolder(savesDir, "", zos);
                }
                
                byte[] zipBytes = java.nio.file.Files.readAllBytes(tempZip.toPath());
                String base64Content = java.util.Base64.getEncoder().encodeToString(zipBytes);
                
                String device = detectDevice();
                String json = "{\"name\":\"full-save-" + escapeJson(username) + "\"," +
                            "\"filename\":\"saves.zip\"," +
                            "\"device\":\"" + escapeJson(device) + "\"," +
                            "\"content\":\"" + base64Content + "\"}";
                
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
                int responseCode = conn.getResponseCode();
                
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                conn.disconnect();
                
                tempZip.delete();
                
                Core.app.post(() -> {
                    if (responseCode == 201 || responseCode == 200) {
                        setStatus(Core.bundle.get("mod.cloudsave.success.upload"));
                        loadSavesList();
                    } else {
                        String errMsg = parseError(response.toString());
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + errMsg);
                        setupUI();
                    }
                });
                
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Cleartext HTTP")) {
                    errorMsg = "HTTP blocked. Use HTTPS or 127.0.0.1";
                }
                final String finalMsg = errorMsg;
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + finalMsg);
                    setupUI();
                });
            }
        }).start();
    }
    
    private void deleteSave(int saveId, String saveName) {
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves/" + saveId;
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        setStatus(Core.bundle.get("mod.cloudsave.status.deleting"));
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                conn.disconnect();
                
                Core.app.post(() -> {
                    if (responseCode == 200) {
                        setStatus(Core.bundle.get("mod.cloudsave.success.delete"));
                        loadSavesList();
                    } else {
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": HTTP " + responseCode);
                        setupUI();
                    }
                });
                
            } catch (Exception e) {
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                    setupUI();
                });
            }
        }).start();
    }
    
    private void downloadSave(int saveId, String saveName) {
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves/" + saveId + "/download";
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        dbg("=== DOWNLOAD START ===");
        dbg("URL: " + url);
        dbg("Token: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null"));
        dbg("Saves dir: " + Vars.saveDirectory.file().getAbsolutePath());
        
        setStatus(Core.bundle.get("mod.cloudsave.status.downloading"));
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);
                
                dbg("Connecting...");
                int responseCode = conn.getResponseCode();
                dbg("Response code: " + responseCode);
                
                if (responseCode == 200) {
                    File tempZip = File.createTempFile("mindustry-download-", ".zip");
                    dbg("Temp file: " + tempZip.getAbsolutePath());
                    
                    try (InputStream is = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(tempZip)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalBytes = 0;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }
                        dbg("Downloaded bytes: " + totalBytes);
                    }
                    
                    dbg("ZIP size: " + tempZip.length() + " bytes");
                    
                    File savesDir = Vars.saveDirectory.file();
                    dbg("Extracting to: " + savesDir.getAbsolutePath());
                    
                    extractZip(tempZip, savesDir);
                    dbg("Extraction complete");
                    
                    // List files after extraction
                    File[] extracted = savesDir.listFiles();
                    if (extracted != null) {
                        for (File f : extracted) {
                            dbg("File in saves: " + f.getName() + " (" + f.length() + " bytes)");
                        }
                    }
                    
                    tempZip.delete();
                    
                    dbg("=== DOWNLOAD OK ===");
                    Core.app.post(() -> {
                        hide();
                        Dialog confirm = new Dialog(Core.bundle.get("mod.cloudsave.restart.title"));
                        confirm.cont.add(Core.bundle.get("mod.cloudsave.restart.warning")).pad(20).row();
                        confirm.cont.add(Core.bundle.get("mod.cloudsave.restart.message")).pad(10).row();
                        
                        confirm.buttons.button(Core.bundle.get("mod.cloudsave.restart.yes"), () -> {
                            confirm.hide();
                            Core.app.exit();
                        }).size(150, 50);
                        
                        confirm.buttons.button(Core.bundle.get("mod.cloudsave.restart.no"), () -> {
                            confirm.hide();
                            setupUI();
                        }).size(150, 50);
                        
                        confirm.show();
                    });
                } else {
                    dbg("HTTP error: " + responseCode);
                    Core.app.post(() -> {
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": HTTP " + responseCode);
                        setupUI();
                    });
                }
                
                conn.disconnect();
                
            } catch (Exception e) {
                dbg("EXCEPTION: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                    setupUI();
                });
            }
        }).start();
    }
    
    private void showSaveDetail(int id, String name, String device, String dateStr, String sizeStr, Table parentItem) {
        parentItem.clear();
        
        // Заголовок с кнопкой свернуть
        Table titleRow = new Table();
        titleRow.defaults().pad(2);
        
        titleRow.button("< " + name, () -> {
            collapseItem(parentItem, name);
        }).width(480).height(32).left().padLeft(10);
        
        parentItem.add(titleRow).width(490).fillX().row();
        
        // Информация
        Table infoTable = new Table();
        infoTable.defaults().pad(2).left();
        
        infoTable.add("  " + Core.bundle.get("mod.cloudsave.detail.device") + ": ").left();
        infoTable.add(device.isEmpty() ? "-" : device).left().row();
        
        infoTable.add("  " + Core.bundle.get("mod.cloudsave.detail.date") + ": ").left();
        infoTable.add(dateStr).left().row();
        
        infoTable.add("  " + Core.bundle.get("mod.cloudsave.detail.size") + ": ").left();
        infoTable.add(sizeStr).left().row();
        
        parentItem.add(infoTable).width(490).fillX().row();
        
        // Поле переименования
        Table renameRow = new Table();
        renameRow.defaults().pad(2);
        
        renameRow.add("  " + Core.bundle.get("mod.cloudsave.detail.rename") + ": ").left();
        TextField renameField = new TextField(name);
        renameRow.add(renameField).width(200).left();
        
        parentItem.add(renameRow).width(490).fillX().row();
        
        // Кнопки действий
        Table buttonRow = new Table();
        buttonRow.defaults().size(90, 32).pad(3);
        
        buttonRow.button(Core.bundle.get("mod.cloudsave.rename"), () -> {
            String newName = renameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(name)) {
                renameSave(id, newName);
            }
        });
        
        buttonRow.button(Core.bundle.get("mod.cloudsave.download"), Icon.download, () -> {
            downloadSave(id, name);
        });
        
        buttonRow.button(Core.bundle.get("mod.cloudsave.delete"), Icon.trash, () -> {
            deleteSave(id, name);
        });
        
        parentItem.add(buttonRow).width(490).fillX().padTop(5).row();
    }
    
    private void collapseItem(Table parentItem, String name) {
        parentItem.clear();
        
        Table row = new Table();
        row.defaults().pad(2);
        
        row.button(name, () -> {
            // Перезагружаем список чтобы развернуть заново
            loadSavesList();
        }).width(480).height(36).left().padLeft(10);
        
        parentItem.add(row).width(490).fillX().pad(2).row();
    }
    
    private void renameSave(int saveId, String newName) {
        String url = CloudSaveMod.getInstance().getConfigManager().getServerUrl() + "/api/saves/" + saveId;
        String token = CloudSaveMod.getInstance().getConfigManager().getAuthToken();
        
        setStatus(Core.bundle.get("mod.cloudsave.status.renaming"));
        
        String json = "{\"name\":\"" + escapeJson(newName) + "\"}";
        
        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                
                int responseCode = conn.getResponseCode();
                conn.disconnect();
                
                Core.app.post(() -> {
                    if (responseCode == 200) {
                        setStatus(Core.bundle.get("mod.cloudsave.success.rename"));
                        loadSavesList();
                    } else {
                        setStatus(Core.bundle.get("mod.cloudsave.error") + ": HTTP " + responseCode);
                    }
                });
            } catch (Exception e) {
                Core.app.post(() -> {
                    setStatus(Core.bundle.get("mod.cloudsave.error") + ": " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void archiveFolder(File folder, String parentPath, java.util.zip.ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            String entryName = parentPath.isEmpty() ? file.getName() : parentPath + "/" + file.getName();
            
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
    
    private void extractZip(File zipFile, File destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("/")) {
                    entryName = entryName.substring(1);
                }
                if (entryName.isEmpty()) continue;
                
                File newFile = new File(destDir, entryName);
                
                String destPath = destDir.getCanonicalPath();
                String filePath = newFile.getCanonicalPath();
                if (!filePath.startsWith(destPath + File.separator)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                
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
    
    private String detectDevice() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("android")) return "Android";
        if (os.contains("linux")) return "Linux-" + arch;
        if (os.contains("mac")) return "macOS";
        if (os.contains("win")) return "Windows";
        return "Unknown";
    }
    
    private String resolveServerUrl(String input) {
        if (input == null || input.trim().isEmpty()) return input;
        
        String trimmed = input.trim();
        
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        
        // Если указан порт (например :3000, :8080) — используем http
        // Если без порта — используем https (через Apache на 443)
        if (trimmed.contains(":")) {
            return "http://" + trimmed;
        }
        
        return "https://" + trimmed;
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
                  .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    
    private String parseError(String responseBody) {
        try {
            Jval json = Jval.read(responseBody);
            if (json.has("error")) {
                return json.getString("error");
            }
        } catch (Exception ignored) {}
        return responseBody;
    }
    
    private void setStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }
    
    private void dbg(String msg) {
        if (CloudSaveMod.getInstance().getConfigManager().isDebugMode()) {
            Log.info("[CloudSave:DBG] " + msg);
        }
    }
}
