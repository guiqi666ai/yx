package com.yx.controller;

import cn.hutool.json.JSONObject;
import com.yx.client.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

import java.util.List;

public class FollowMyController {

    @FXML private ListView<HBox> fansList;
    @FXML private Label msgLabel;

    private ApiClient apiClient;
    private String username;

    public void init(ApiClient apiClient, String username) {
        this.apiClient = apiClient;
        this.username = username;
        handleRefresh();
    }

    @FXML
    public void handleRefresh() {
        List<JSONObject> fans = apiClient.getFollowMy();
        fansList.getItems().clear();

        if (fans.isEmpty()) {
            msgLabel.setText("还没有人关注你");
            return;
        }

        msgLabel.setText("共 " + fans.size() + " 人关注你");

        for (JSONObject user : fans) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8; -fx-alignment: center-left;");

            String nickname = user.getStr("nickname");
            String targetUser = user.getStr("username");
            Boolean isFollowed = user.getBool("isFollowed", false);

            Label nameLabel = new Label(nickname != null ? nickname : targetUser);
            nameLabel.setPrefWidth(200);
            row.getChildren().add(nameLabel);

            // 只有“我还没关注他”时才显示“关注”按钮
            if (!Boolean.TRUE.equals(isFollowed)) {
                Button followBtn = new Button("关注");
                followBtn.setOnAction(e -> {
                    boolean ok = apiClient.follow(targetUser);
                    if (ok) {
                        msgLabel.setText("已关注 " + targetUser);
                        handleRefresh();   // 刷新后按钮消失
                    } else {
                        msgLabel.setText("关注失败");
                    }
                });
                row.getChildren().add(followBtn);
            }
            // else：已关注，不加任何按钮

            fansList.getItems().add(row);
        }
    }
}