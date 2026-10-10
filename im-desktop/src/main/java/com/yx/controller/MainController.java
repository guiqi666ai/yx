package com.yx.controller;

import com.yx.client.ApiClient;
import com.yx.client.ImClient;
import com.yx.entity.Message;
import com.yx.entity.MsgType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.function.Consumer;

public class MainController {

    @FXML private TabPane tabPane;

    /** 群邀请消息的业务类型值 */
    private static final int MSG_TYPE_GROUP_INVITE = 7;
    /** 系统消息发送者标识 */
    private static final String SYSTEM_SENDER = "im系统";

    /** 全局群邀请监听器，避免和 ChatController 重复 */
    private Consumer<Message> globalInviteListener;

    /**
     * 由 LoginController 调用（原有方法，不动）
     */
    public void init(String username, boolean isAdmin, ApiClient apiClient) {
        if (!isAdmin) {
            return;   // 不是管理员，不加 Tab
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/admin.fxml"));
            Node adminContent = loader.load();

            // 拿到 AdminController，注入 apiClient
            AdminController adminController = loader.getController();
            adminController.init(apiClient);   // ← 关键

            Tab adminTab = new Tab("管理员", adminContent);
            tabPane.getTabs().add(adminTab);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ★ 由 LoginController 调用：注册全局群邀请监听器。
     * 只有管理员才需要弹窗处理入群申请；普通用户直接忽略。
     *
     * @param client    Netty 客户端
     * @param apiClient HTTP 客户端
     * @param username  当前登录用户名
     * @param isAdmin   是否管理员（非管理员不注册监听器）
     */
    public void initGlobalListener(ImClient client,
                                   ApiClient apiClient,
                                   String username,
                                   boolean isAdmin) {
        if (!isAdmin) {
            return;   // 普通用户不处理入群申请
        }
        if (client == null) {
            System.err.println("initGlobalListener: client 为空，无法注册监听器");
            return;
        }

        globalInviteListener = msg -> {
            boolean isInvite =
                    MsgType.fromCode(msg.getType()) == MsgType.GROUP_INVITE_REQUEST
                            || (SYSTEM_SENDER.equals(msg.getFromUserName())
                            && msg.getMsgType() == MSG_TYPE_GROUP_INVITE);
            if (!isInvite) {
                return;
            }
            Platform.runLater(() -> showInviteDialog(msg, apiClient));
        };

        client.addMessageListener(globalInviteListener);
    }

    /** 弹出入群申请审批对话框 */
    private void showInviteDialog(Message msg, ApiClient apiClient) {
        try {
            // 1. 只处理来自"im系统"的消息
            if (!SYSTEM_SENDER.equals(msg.getFromUserName())) {
                return;
            }

            // 2. 解析 inviteeName
            String content = msg.getContent();
            if (content == null || !content.startsWith("inviteeName:")) {
                System.err.println("入群申请 content 格式异常: " + content);
                return;
            }
            String inviteeName = content.substring("inviteeName:".length()).trim();
            if (inviteeName.isEmpty()) {
                System.err.println("入群申请 inviteeName 为空");
                return;
            }

            // 3. 解析 groupId
            String groupIdStr = msg.getGroupId();
            if (groupIdStr == null || groupIdStr.isEmpty()) {
                System.err.println("入群申请缺少 groupId, msgId=" + msg.getMsgId()
                        + ", content=" + content);
                showInfo("入群申请", "无法获取群信息，请到聊天窗口手动处理");
                return;
            }
            Long groupId = Long.valueOf(groupIdStr);

            // 4. 弹窗
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("入群申请");
            alert.setHeaderText("有用户申请入群");
            alert.setContentText("用户「" + inviteeName + "」申请加入群 "
                    + groupId + "，是否同意？");

            ButtonType agreeBtn = new ButtonType("同意");
            ButtonType rejectBtn = new ButtonType("拒绝");
            alert.getButtonTypes().setAll(agreeBtn, rejectBtn);

            alert.showAndWait().ifPresent(btn -> {
                boolean isAgree = (btn == agreeBtn);

                new Thread(() -> {
                    boolean ok = apiClient.agreeOrReject(isAgree, groupId, inviteeName);
                    Platform.runLater(() -> {
                        if (ok) {
                            showInfo("入群申请",
                                    "已" + (isAgree ? "同意" : "拒绝")
                                            + "「" + inviteeName + "」入群");
                        } else {
                            showInfo("入群申请",
                                    "处理失败，该申请可能已被其他管理员处理");
                        }
                    });
                }).start();
            });

        } catch (Exception e) {
            e.printStackTrace();
            showInfo("入群申请", "处理入群申请失败：" + e.getMessage());
        }
    }

    /** 统一的提示框 */
    private void showInfo(String title, String content) {
        Alert tip = new Alert(Alert.AlertType.INFORMATION);
        tip.setTitle(title);
        tip.setHeaderText(null);
        tip.setContentText(content);
        tip.show();
    }
}