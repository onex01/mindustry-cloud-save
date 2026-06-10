package com.onex01.cloudsave.ui;

import arc.scene.ui.Dialog;
import arc.scene.ui.TextField;
import arc.util.Log;
import com.onex01.cloudsave.network.ApiClient;

public class LoginDialog extends Dialog {
    
    private final ApiClient apiClient;
    private TextField usernameField;
    private TextField passwordField;
    private Runnable onLoginSuccess;
    
    public LoginDialog(ApiClient apiClient) {
        super("☁️ Cloud Save - Вход");
        
        this.apiClient = apiClient;
        
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
            Dialog errorDialog = new Dialog("Ошибка");
            errorDialog.cont.add("Заполните все поля");
            errorDialog.buttons.button("OK", () -> {});
            errorDialog.show();
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
                Dialog errorDialog = new Dialog("Ошибка входа");
                errorDialog.cont.add("Ошибка: " + error);
                errorDialog.buttons.button("OK", () -> {});
                errorDialog.show();
            }
        });
    }
    
    private void performRegister() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            Dialog errorDialog = new Dialog("Ошибка");
            errorDialog.cont.add("Заполните все поля");
            errorDialog.buttons.button("OK", () -> {});
            errorDialog.show();
            return;
        }
        
        Log.info("[CloudSave] Попытка регистрации для пользователя: " + username);
        
        apiClient.register(username, password, new ApiClient.Callback() {
            @Override
            public void onSuccess(String response) {
                Log.info("[CloudSave] Успешная регистрация!");
                
                Dialog successDialog = new Dialog("Успех");
                successDialog.cont.add("Регистрация успешна!\nТеперь войдите в систему.");
                successDialog.buttons.button("OK", () -> {
                    performLogin();
                });
                successDialog.show();
            }
            
            @Override
            public void onFailure(String error) {
                Dialog errorDialog = new Dialog("Ошибка регистрации");
                errorDialog.cont.add("Ошибка: " + error);
                errorDialog.buttons.button("OK", () -> {});
                errorDialog.show();
            }
        });
    }
    
    public void setOnLoginSuccess(Runnable callback) {
        this.onLoginSuccess = callback;
    }
}