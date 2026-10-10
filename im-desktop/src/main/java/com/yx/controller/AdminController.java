package com.yx.controller;


import com.yx.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class AdminController {

    @FXML private TextField targetUserField;
    @FXML private ChoiceBox<String> statusChoice;
    @FXML private Label msgLabel;

    private ApiClient apiClient;

    @FXML
    public void initialize() {
        statusChoice.getItems().addAll("1 启用", "0 禁用");
        statusChoice.setValue("1 启用");   // 默认选中"启用"
    }

    public void init(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @FXML
    public void handleChangeStatus() {
        String targetUser = targetUserField.getText().trim();
        if (targetUser.isEmpty()) {
            msgLabel.setText("请输入用户名");
            return;
        }

        String value = statusChoice.getValue();
        if (value == null) {
            msgLabel.setText("请选择状态");
            return;
        }

        int status = value.startsWith("1") ? 1 : 0;
        boolean ok = apiClient.changeStatus(targetUser, status);
        msgLabel.setText(ok ? "修改成功" : "修改失败");
    }
}