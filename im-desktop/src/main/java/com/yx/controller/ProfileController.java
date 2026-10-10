package com.yx.controller;

import com.yx.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ProfileController {

    @FXML private TextField nicknameField;
    @FXML
    private PasswordField newPasswordField;
    @FXML private Label msgLabel;

    private ApiClient apiClient;

    public void init(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @FXML
    public void handleSave() {
        String nickname = nicknameField.getText().trim();
        String password = newPasswordField.getText().trim();
        boolean ok = apiClient.updatePersonalInfo(
                nickname.isEmpty() ? null : nickname,
                password.isEmpty() ? null : password);
        msgLabel.setText(ok ? "保存成功" : "保存失败");
    }

    @FXML
    public void handleRevoke() {
        // 二次确认后调 apiClient.revoke()
    }

    @FXML
    public void handleLogout() {
        apiClient.logout();
        System.exit(0);
    }
}