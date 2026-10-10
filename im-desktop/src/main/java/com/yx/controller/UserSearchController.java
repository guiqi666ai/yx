package com.yx.controller;

import cn.hutool.json.JSONObject;
import com.yx.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public class UserSearchController {

    @FXML private TextField searchField;
    @FXML private ListView<HBox> resultList;
    @FXML private Label msgLabel;

    private ApiClient apiClient;
    private String username;

    /** 由 LoginController 调用 */
    public void init(ApiClient apiClient, String username) {
        this.apiClient = apiClient;
        this.username = username;
    }

    // ==================== 搜索 ====================

    @FXML
    public void handleSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            msgLabel.setText("请输入用户名");
            return;
        }

        JSONObject user = apiClient.searchUser(keyword);
        resultList.getItems().clear();

        if (user == null) {
            msgLabel.setText("未找到用户");
            return;
        }

        msgLabel.setText("找到用户");
        resultList.getItems().add(buildUserRow(user));
    }

    // ==================== 用户行 ====================

    /**
     * 构造一行：昵称 + 关注/取关 按钮
     * 假设 UserVo 返回字段：userName, nickname, isFollowed (0/1 或 true/false)
     */
    private HBox buildUserRow(JSONObject user) {
        HBox row = new HBox(10);
        row.setStyle("-fx-padding: 8; -fx-alignment: center-left;");

        String username = user.getStr("username");
        String nickname = user.getStr("nickname");

        Label nameLabel = new Label(nickname != null ? nickname : username);
        nameLabel.setPrefWidth(200);

        Button actionBtn = new Button();
        updateButtonText(actionBtn, user);   // 根据 user 里的 isFollowed 设置文字

        actionBtn.setOnAction(e -> {
            boolean currentFollowed = Boolean.TRUE.equals(user.getBool("isFollowed", false));
            boolean ok;
            if (currentFollowed) {
                ok = apiClient.cancelFollow(username);
                if (ok) {
                    user.set("isFollowed", false);
                    updateButtonText(actionBtn, user);
                }
            } else {
                ok = apiClient.follow(username);
                if (ok) {
                    user.set("isFollowed", true);
                    updateButtonText(actionBtn, user);
                }
            }
            msgLabel.setText(ok ? "操作成功" : "操作失败");
        });

        row.getChildren().addAll(nameLabel, actionBtn);
        return row;
    }

    /** 根据 user 当前 isFollowed 状态设置按钮文字 */
    private void updateButtonText(Button btn, JSONObject user) {
        boolean followed = Boolean.TRUE.equals(user.getBool("isFollowed", false));
        btn.setText(followed ? "取关" : "关注");
    }
}