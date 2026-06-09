package com.onex01.cloudsave.ui;

import arc.scene.ui.Dialog;
import arc.scene.ui.TextField;
import arc.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.onex01.cloudsave.network.ApiClient;

public class LoginDialog extends Dialog {
    
    private final ApiClient apiClient;
    private final Gson gson;
    private TextField usernameField;
    private TextField passwordField;
    private Runnable onLoginSuccess;
    
    public LoginDialog(ApiClient apiClient) {
        super("☁️ Cloud Save - Вход");
        
        this.apiClient = apiClient;
        this.gson = new Gson();
        
        setupUI();
    }
    
    private void setupUI() {
        cont.defaults().pad(10);
        
        cont.add("Имя пользователя:").row();
        usernameField = new TextField("");
        usernameField.setMessageText("Введите логин");
        cont.add(usernameField).width(300).row();
        
        cont.add("Пароль:").row();
        passwordField = new TextField("");
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('•');
        passwordField.setMessageText("Введите пароль");
        cont.add(passwordField).width(300).row();
        
        cont.add().height(20).row();
        
        // Кнопки
        buttons.defaults().size(150, 50).pad(5);
        
        buttons.button("Вход", () -> {
            performLogin();
        });
        
        buttons.button("Регистрация", () -> {
            performRegister();
        });
        
        buttons.button("Отмена", this::hide);
    }
    
    private void performLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            new Dialog("Ошибка").text("Заполните все поля").buttons().button("OK", () -> {}).show();
            return;
        }
        
        Log.info("[CloudSave] Попытка входа для пользователя: " + username);
        
        apiClient.login(username, password, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Log.info("[CloudSave] Успешный вход!");
                
                if (onLoginSuccess != null) {
                    onLoginSuccess.run();
                }
            }
            
            @Override
            public void onFailure(String error) {
                new Dialog("Ошибка входа").text("Ошибка: " + error)
                    .buttons().button("OK", () -> {}).show();
            }
        });
    }
    
    private void performRegister() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            new Dialog("Ошибка").text("Заполните все поля").buttons().button("OK", () -> {}).show();
            return;
        }
        
        Log.info("[CloudSave] Попытка регистрации для пользователя: " + username);
        
        apiClient.register(username, password, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Log.info("[CloudSave] Успешная регистрация!");
                
                new Dialog("Успех").text("Регистрация успешна!\nТеперь войдите в систему.")
                    .buttons().button("OK", () -> {
                        // Автоматически пытаемся войти
                        performLogin();
                    }).show();
            }
            
            @Override
            public void onFailure(String error) {
                new Dialog("Ошибка регистрации").text("Ошибка: " + error)
                    .buttons().button("OK", () -> {}).show();
            }
        });
    }
    
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }
}