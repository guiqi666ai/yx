package com.yx.controller;

import cn.hutool.json.JSONObject;
import com.yx.client.ApiClient;
import com.yx.client.ImClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.List;

public class GroupListController {

    @FXML private ListView<HBox> groupListView;
    @FXML private Label msgLabel;

    private ImClient client;
    private ApiClient apiClient;
    private String username;

    /** 由 LoginController 或 MainController 调用 */
    public void init(ImClient client, ApiClient apiClient, String username) {
        this.client = client;
        this.apiClient = apiClient;
        this.username = username;
        handleRefresh();
    }

    @FXML
    public void handleRefresh() {
        try {
            List<JSONObject> groups = apiClient.getMyGroups();
            groupListView.getItems().clear();

            if (groups == null) {
                msgLabel.setText("加载群列表失败");
                return;
            }

            if (groups.isEmpty()) {
                msgLabel.setText("还没有加入任何群");
                return;
            }

            msgLabel.setText("共 " + groups.size() + " 个群");

            for (JSONObject group : groups) {
                HBox row = new HBox(10);
                row.setStyle("-fx-padding: 8; -fx-alignment: center-left;");

                Long groupId = group.getLong("groupId");
                String groupName = group.getStr("groupName");
                Integer memberCount = group.getInt("memberCount", 0);
                Integer myRole = group.getInt("myRole", 3);

                String roleText = switch (myRole) {
                    case 1 -> "[群主]";
                    case 2 -> "[管理员]";
                    default -> "";
                };

                Label nameLabel = new Label(roleText + " " + groupName
                        + " (" + memberCount + "人)");
                nameLabel.setPrefWidth(250);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button enterBtn = new Button("进入群聊");
                enterBtn.setOnAction(e -> openGroupChat(groupId, groupName));

                row.getChildren().addAll(nameLabel, spacer, enterBtn);
                groupListView.getItems().add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
            msgLabel.setText("加载群列表失败：" + e.getMessage());
        }
    }

    /** 打开群聊窗口 */
    private void openGroupChat(Long groupId, String groupName) {
        if (groupId == null || groupName == null) {
            msgLabel.setText("群信息异常，无法打开");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/chat.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            chatController.init(client, apiClient, username);
            chatController.setToGroup(groupId, groupName);   // ★ 群聊模式

            Stage stage = new Stage();
            stage.setTitle("群聊 - " + groupName);
            stage.setScene(new Scene(root, 600, 500));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            msgLabel.setText("打开群聊失败：" + e.getMessage());
        }
    }

    @FXML
    public void handleCreateGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("创建群");
        dialog.setHeaderText("输入群名");
        dialog.setContentText("群名：");

        dialog.showAndWait().ifPresent(groupName -> {
            String name = groupName.trim();
            if (name.isEmpty()) {
                msgLabel.setText("群名不能为空");
                return;
            }
            new Thread(() -> {
                boolean ok = apiClient.createGroup(name);
                Platform.runLater(() -> {
                    msgLabel.setText(ok ? "创建成功" : "创建失败");
                    if (ok) handleRefresh();
                });
            }).start();
        });
    }
}