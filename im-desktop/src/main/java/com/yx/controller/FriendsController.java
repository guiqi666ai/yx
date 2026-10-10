package com.yx.controller;

import cn.hutool.json.JSONObject;
import com.yx.client.ApiClient;
import com.yx.client.ImClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendsController {

    @FXML private ListView<HBox> friendList;
    @FXML private Label msgLabel;

    private ImClient client;
    private ApiClient apiClient;
    private String username;

    /** ★ 缓存未读发送者，方便局部刷新 */
    private final Set<String> unreadSenders = new HashSet<>();

    public void init(ImClient client, ApiClient apiClient, String username) {
        this.client = client;
        this.apiClient = apiClient;
        this.username = username;

        // ★ 注册消息监听器：收到新消息时刷新红点
        client.addMessageListener(msg -> {
            String from = msg.getFromUserName();
            if (from != null && !from.equals(username)) {
                unreadSenders.add(from);
                Platform.runLater(this::refreshUI);
            }
        });

        handleRefresh();
    }

    @FXML
    public void handleRefresh() {
        new Thread(() -> {
            List<JSONObject> friends = apiClient.getFriends();
            List<String> unread = apiClient.getUnreadSenders();

            Platform.runLater(() -> {
                unreadSenders.clear();
                unreadSenders.addAll(unread);
                renderFriends(friends);
            });
        }).start();
    }

    private void renderFriends(List<JSONObject> friends) {
        friendList.getItems().clear();

        if (friends.isEmpty()) {
            msgLabel.setText("还没有好友，去搜索并互关吧");
            return;
        }

        msgLabel.setText("共 " + friends.size() + " 个好友");

        for (JSONObject friend : friends) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 8; -fx-alignment: center-left;");

            String nickname = friend.getStr("nickname");
            String targetUser = friend.getStr("username");

            Label nameLabel = new Label(nickname != null ? nickname : targetUser);
            nameLabel.setPrefWidth(200);

            row.getChildren().add(nameLabel);

            // ★ 红点
            if (unreadSenders.contains(targetUser)) {
                Label redDot = new Label("●");
                redDot.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");
                row.getChildren().add(redDot);
            }

            Button chatBtn = new Button("聊天");
            chatBtn.setOnAction(e -> openChatWith(targetUser));

            row.getChildren().add(chatBtn);
            friendList.getItems().add(row);
        }
    }

    /** 局部刷新：只更新红点 */
    private void refreshUI() {
        for (int i = 0; i < friendList.getItems().size(); i++) {
            HBox row = friendList.getItems().get(i);
            // 从行里取 username（第 1 个 Label 的文字就是昵称或用户名，不好反查）
            // 简化做法：整个列表重渲染
        }
        handleRefresh();   // 直接重拉一次
    }

    /** 关闭聊天窗口时调，用来清红点 */
    public void markRead(String targetUser) {
        unreadSenders.remove(targetUser);
        handleRefresh();
    }

    private void openChatWith(String targetUser) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            chatController.init(client, apiClient, username);
            chatController.setToUser(targetUser);

            // ★ 把 FriendsController 传进去，方便清红点
            chatController.setFriendsController(this);

            Stage stage = new Stage();
            stage.setTitle("聊天 - " + targetUser);
            stage.setScene(new Scene(root, 600, 500));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}