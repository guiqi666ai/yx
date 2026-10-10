package com.yx.controller;

import cn.hutool.json.JSONObject;
import com.yx.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

import java.util.List;

public class MyFollowController {

    @FXML private ListView<HBox> followList;
    @FXML private Label msgLabel;

    private ApiClient apiClient;
    private String username;

    /** 由 LoginController 调用 */
    public void init(ApiClient apiClient, String username) {
        this.apiClient = apiClient;
        this.username = username;
        handleRefresh();
    }

    @FXML
    public void handleRefresh() {
        List<JSONObject> follows = apiClient.getMyFollow();
        followList.getItems().clear();

        if (follows.isEmpty()) {
            msgLabel.setText("还没有关注任何人");
            return;
        }

        msgLabel.setText("共关注 " + follows.size() + " 人");

        for (JSONObject user : follows) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8; -fx-alignment: center-left;");

            String targetUser = user.getStr("username");
            String nickname = user.getStr("nickname");

            Label nameLabel = new Label(nickname != null ? nickname : targetUser);
            nameLabel.setPrefWidth(200);

            Button cancelBtn = new Button("取关");
            cancelBtn.setOnAction(e -> {
                boolean ok = apiClient.cancelFollow(targetUser);
                if (ok) {
                    msgLabel.setText("已取关 " + targetUser);
                    handleRefresh();   // 刷新列表
                } else {
                    msgLabel.setText("取关失败");
                }
            });

            row.getChildren().addAll(nameLabel, cancelBtn);
            followList.getItems().add(row);
        }
    }
}