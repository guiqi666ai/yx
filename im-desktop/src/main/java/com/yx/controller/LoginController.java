package com.yx.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.yx.client.ApiClient;
import com.yx.client.ImClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private static final String serverField= "127.0.0.1";
    private final ObjectMapper mapper = new ObjectMapper();

    // ==================== 登录 ====================

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String server = serverField;

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("用户名和密码不能为空");
            return;
        }

        try {
            // 1. 用 ApiClient 登录
            ApiClient apiClient = new ApiClient(server, 8080);
            String token = apiClient.login(username, password);
            apiClient.setAccessToken(token);

            if (token == null || token.isEmpty()) {
                errorLabel.setText("登录失败：用户名或密码错误");
                return;
            }

            // 2. 从 token 解析角色，判断是否管理员
            boolean isAdmin = parseIsAdmin(token);

            // 3. 连接 Netty
            ImClient client = new ImClient(server, 8888, mapper, username, token);
            new Thread(() -> {
                try {
                    client.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            // 4. 打开主窗口
            openMainWindow(client, apiClient, username, isAdmin);

        } catch (Exception e) {
            errorLabel.setText("登录异常：" + e.getMessage());
            e.printStackTrace();
        }
    }
    private boolean parseIsAdmin(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;

            // JWT payload 是 Base64URL 编码
            String payload = new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1]),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(payload);
            String role = json.getStr("role");
            return "admin".equals(role);
        } catch (Exception e) {
            return false;
        }
    }
    private void openMainWindow(ImClient client, ApiClient apiClient,
                                String username, boolean isAdmin) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/main.fxml"));
                Parent root = loader.load();

                // 1. 主 Controller
                MainController mainController = loader.getController();
                mainController.init(username, isAdmin, apiClient);

                // ★★★ 关键改动：注册全局群邀请监听器（仅管理员生效）
                mainController.initGlobalListener(client, apiClient, username, isAdmin);
                mainController.initGlobalListener(client, apiClient, username, isAdmin);  // ★ 加这行
                // 2. 个人信息
                ProfileController profileController = (ProfileController) loader.getNamespace()
                        .get("profileController");
                if (profileController != null) {
                    profileController.init(apiClient);
                }

                // 3. 用户搜索
                UserSearchController userSearchController = (UserSearchController) loader.getNamespace()
                        .get("userSearchController");
                if (userSearchController != null) {
                    userSearchController.init(apiClient, username);
                }

                // 4. 我的关注
                MyFollowController myFollowController = (MyFollowController) loader.getNamespace()
                        .get("myFollowController");
                if (myFollowController != null) {
                    myFollowController.init(apiClient, username);
                }

                // 5. 关注我的
                FollowMyController followMyController = (FollowMyController) loader.getNamespace()
                        .get("followMyController");
                if (followMyController != null) {
                    followMyController.init(apiClient, username);
                }

                // 6. 好友 —— 传 client、apiClient、username
                FriendsController friendsController = (FriendsController) loader.getNamespace()
                        .get("friendsController");
                if (friendsController != null) {
                    friendsController.init(client, apiClient, username);
                }
                GroupListController groupListController = (GroupListController) loader.getNamespace()
                        .get("groupListController");
                if (groupListController != null) {
                    groupListController.init(client, apiClient, username);
                }
                // 7. 显示主窗口
                Stage stage = new Stage();
                stage.setTitle("IM - " + username);
                stage.setScene(new Scene(root, 700, 500));
                stage.setOnCloseRequest(e -> System.exit(0));
                stage.show();

                ((Stage) usernameField.getScene().getWindow()).close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    // ==================== 用户注册 ====================

    @FXML
    public void handleRegisterUser() {
        register();
    }

    private void register() {
        // 弹窗输入用户名
        TextField usernameInput = new TextField();
        usernameInput.setPromptText("用户名");

        // 弹窗输入密码
        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("密码");

        VBox vbox = new VBox(10,
                new Label("用户名："), usernameInput,
                new Label("密码："), passwordInput);
        vbox.setPadding(new javafx.geometry.Insets(10));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("用户注册");
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                String username = usernameInput.getText().trim();
                String password = passwordInput.getText().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    errorLabel.setText("用户名和密码不能为空");
                    return;
                }

                try {
                    ApiClient apiClient = new ApiClient(serverField, 8080);
                    boolean ok = apiClient.registerUser(username, password);

                    Alert alert = new Alert(ok
                            ? Alert.AlertType.INFORMATION
                            : Alert.AlertType.ERROR);
                    alert.setTitle("注册结果");
                    alert.setHeaderText(null);
                    alert.setContentText(ok ? "注册成功，请登录" : "注册失败");
                    alert.show();

                    if (ok) {
                        usernameField.setText(username);
                    }
                } catch (Exception e) {
                    errorLabel.setText("注册异常：" + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }
}