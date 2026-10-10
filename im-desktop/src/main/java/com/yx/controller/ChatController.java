package com.yx.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.ImDesktopApp;
import com.yx.client.ApiClient;
import com.yx.client.ImClient;
import com.yx.entity.Message;
import com.yx.entity.MessageFactory;
import com.yx.entity.MsgType;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class ChatController {

    @FXML private Label titleLabel;
    @FXML private Label toUserLabel;
    @FXML private ListView<Node> messageList;
    @FXML private TextField inputField;
    @FXML private MenuButton groupMenu;

    /** 群聊模式：当前群 ID */
    private Long currentGroupId;
    /** 群聊模式：当前群名 */
    private String currentGroupName;
    /** 是否群聊模式 */
    private boolean isGroupChat = false;

    /** Node → msgId 映射 */
    private final Map<Node, Long> nodeMsgIdMap = new HashMap<>();
    /** Node → 创建时间（毫秒），撤回时间判断用 */
    private final Map<Node, Long> nodeTimeMap = new HashMap<>();
    /** 每个会话是否还有更多历史，key 是 chatKey */
    private final Map<String, Boolean> hasMoreMap = new HashMap<>();
    /** 还没收到 ACK 的临时 msgId */
    private final Set<Long> pendingMsgIds = new HashSet<>();

    private ImClient client;
    private ApiClient apiClient;
    private String username;
    private String currentToUser;

    private Consumer<Message> messageListener;

    /** 每个会话对应一个消息列表，key 是 chatKey（单聊是对方用户名，群聊是 group:groupId） */
    private final Map<String, ObservableList<Node>> chatHistoryMap = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 关联的好友列表 Controller，用于清红点 */
    private FriendsController friendsController;

    public void setFriendsController(FriendsController friendsController) {
        this.friendsController = friendsController;
    }
    /** 撤回时间限制：2 分钟 */
    private static final long RECALL_LIMIT_MS = 2 * 60 * 1000;

    @FXML
    public void initialize() {
        messageList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void init(ImClient client, ApiClient apiClient, String username) {
        this.client = client;
        this.apiClient = apiClient;
        this.username = username;
        titleLabel.setText("当前用户：" + username);
        toUserLabel.setText("接收者：未选择");

        messageListener = msg -> Platform.runLater(() -> handleIncomingMessage(msg));
        client.addMessageListener(messageListener);
    }

    @FXML
    public void handleClose() {
        if (messageListener != null && client != null) {
            client.removeMessageListener(messageListener);
        }
        ((Stage) titleLabel.getScene().getWindow()).close();
    }

    // ==================== 会话切换 ====================

    /** 当前会话的 key：单聊是对方用户名，群聊是 group:groupId */
    private String currentChatKey() {
        return isGroupChat ? "group:" + currentGroupId : currentToUser;
    }

    /** 切换到单聊 */
    public void setToUser(String targetUser) {
        this.currentToUser = targetUser;
        this.isGroupChat = false;
        this.currentGroupId = null;
        this.currentGroupName = null;

        toUserLabel.setText("接收者：" + targetUser);

        // 隐藏群操作菜单
        groupMenu.setVisible(false);
        groupMenu.setManaged(false);

        if (targetUser == null || targetUser.isEmpty()) {
            return;
        }

        hasMoreMap.put(targetUser, true);

        new Thread(() -> {
            List<JSONObject> recent = apiClient.getRecent(targetUser);

            Platform.runLater(() -> {
                ObservableList<Node> existing = chatHistoryMap.get(targetUser);
                ObservableList<Node> list = FXCollections.observableArrayList();

                for (JSONObject msgJson : recent) {
                    Message msg = jsonToMessage(msgJson);
                    boolean isSelf = username.equals(msg.getFromUserName());
                    list.add(buildMessageNodeAndTrack(msg, isSelf));
                }

                if (existing != null) {
                    list.addAll(existing);
                }

                chatHistoryMap.put(targetUser, list);
                messageList.setItems(list);
                if (!list.isEmpty()) {
                    messageList.scrollTo(list.size() - 1);
                }
            });

            try {
                apiClient.readMessage(targetUser);
                // ★ 通知好友列表清红点
                if (friendsController != null) {
                    Platform.runLater(() -> friendsController.markRead(targetUser));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /** 切换到群聊 */
    public void setToGroup(Long groupId, String groupName) {
        this.isGroupChat = true;
        this.currentGroupId = groupId;
        this.currentGroupName = groupName;
        this.currentToUser = null;

        toUserLabel.setText("群聊：" + groupName);

        // 显示群操作菜单
        groupMenu.setVisible(true);
        groupMenu.setManaged(true);

        String chatKey = "group:" + groupId;
        hasMoreMap.put(chatKey, true);

        new Thread(() -> {
            List<JSONObject> recent = apiClient.getGroupRecent(groupId);

            Platform.runLater(() -> {
                ObservableList<Node> list = FXCollections.observableArrayList();
                for (JSONObject msgJson : recent) {
                    Message msg = jsonToMessage(msgJson);
                    boolean isSelf = username.equals(msg.getFromUserName());
                    list.add(buildMessageNodeAndTrack(msg, isSelf));
                }
                chatHistoryMap.put(chatKey, list);
                messageList.setItems(list);
                if (!list.isEmpty()) {
                    messageList.scrollTo(list.size() - 1);
                }
            });
        }).start();
    }

    private Node buildMessageNodeAndTrack(Message msg, boolean isSelf) {
        Node node = buildMessageNode(msg, isSelf);
        nodeMsgIdMap.put(node, msg.getMsgId());

        long createTime = msg.getTimestamp();
        if (createTime <= 0) {
            createTime = System.currentTimeMillis();
        }
        nodeTimeMap.put(node, createTime);

        return node;
    }

    private Message jsonToMessage(JSONObject json) {
        Message msg = new Message();
        msg.setMsgId(json.getLong("msgId", 0L));
        msg.setFromUserName(json.getStr("fromUserName"));
        msg.setToUserName(json.getStr("toUserName"));
        msg.setGroupId(json.getStr("groupId"));
        msg.setContent(json.getStr("content"));
        msg.setMsgType(json.getInt("msgType", 1));
        msg.setFileUrl(json.getStr("fileUrl"));
        msg.setFileName(json.getStr("fileName"));
        msg.setFileSize(json.getLong("fileSize"));
        msg.setMimeType(json.getStr("mimeType"));
        msg.setInviteStatus(json.getInt("inviteStatus", 0));
        msg.setFromUserRole(json.getInt("fromUserRole", 3));
        String createTime = json.getStr("createTime");
        if (createTime != null) {
            try {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(
                        createTime.replace(" ", "T"));
                msg.setTimestamp(ldt.atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli());
            } catch (Exception e) {
                msg.setTimestamp(System.currentTimeMillis());
            }
        } else {
            msg.setTimestamp(System.currentTimeMillis());
        }
        return msg;
    }

    // ==================== 收到消息 ====================

    private void handleIncomingMessage(Message msg) {
        MsgType type = MsgType.fromCode(msg.getType());

        if (type == MsgType.RECALL) { handleRecall(msg); return; }
        if (type == MsgType.ACK) { handleAck(msg); return; }

        String fromUser = msg.getFromUserName();
        boolean isSelf = username.equals(fromUser);

        // 群邀请消息（msgType == 7）：虽然是单聊形式，但带了 groupId，
        // 必须优先走单聊分支，落到 "im系统" 会话里，不能让群聊分支截走。
        boolean isGroupInvite = (msg.getMsgType() == 7);

        // 群聊消息：有 groupId 且不是群邀请
        if (!isGroupInvite
                && msg.getGroupId() != null && !msg.getGroupId().isEmpty()) {
            String chatKey = "group:" + msg.getGroupId();
            ObservableList<Node> history = chatHistoryMap.computeIfAbsent(
                    chatKey, k -> FXCollections.observableArrayList());
            Node node = buildMessageNodeAndTrack(msg, isSelf);
            history.add(node);

            if (isGroupChat && String.valueOf(currentGroupId).equals(msg.getGroupId())) {
                messageList.scrollTo(history.size() - 1);
            }
            return;
        }

        // 单聊消息（含群邀请）
        String toUser = msg.getToUserName();
        String otherUser = isSelf ? toUser : fromUser;
        ObservableList<Node> history = chatHistoryMap.computeIfAbsent(
                otherUser, k -> FXCollections.observableArrayList());
        Node node = buildMessageNodeAndTrack(msg, isSelf);
        history.add(node);

        if (!isGroupChat && otherUser.equals(currentToUser)) {
            messageList.scrollTo(history.size() - 1);
        }
    }
    private void handleAck(Message msg) {
        String content = msg.getContent();
        if (content == null || !content.contains(":")) return;

        try {
            String[] parts = content.split(":");
            Long tempMsgId = Long.valueOf(parts[0]);
            Long realMsgId = Long.valueOf(parts[1]);

            pendingMsgIds.remove(tempMsgId);

            for (ObservableList<Node> history : chatHistoryMap.values()) {
                for (Node node : history) {
                    Long id = nodeMsgIdMap.get(node);
                    if (tempMsgId.equals(id)) {
                        nodeMsgIdMap.put(node, realMsgId);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleRecall(Message msg) {
        Long recallMsgId = msg.getMsgId();
        boolean isSelf = username.equals(msg.getFromUserName());
        replaceLocalMessage(recallMsgId, isSelf);
    }

    private void replaceLocalMessage(Long msgId, boolean isSelf) {
        for (ObservableList<Node> history : chatHistoryMap.values()) {
            for (int i = 0; i < history.size(); i++) {
                Node node = history.get(i);
                Long id = nodeMsgIdMap.get(node);
                if (msgId.equals(id)) {
                    Node recallNode = buildRecallNode(isSelf);
                    history.set(i, recallNode);
                    nodeMsgIdMap.remove(node);
                    nodeTimeMap.remove(node);
                    nodeMsgIdMap.put(recallNode, msgId);
                    return;
                }
            }
        }
    }

    private Node buildRecallNode(boolean isSelf) {
        Label label = new Label("[已撤回一条消息]");
        label.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");

        HBox row = new HBox(label);
        row.setStyle("-fx-padding: 4 8 4 8;");
        row.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    // ==================== 消息显示 ====================

    private Node buildMessageNode(Message msg, boolean isSelf) {
        Node content;
        switch (msg.getMsgType()) {
            case 1:
                content = new Label(msg.getContent());
                break;
            case 2:
                content = buildImageNode("", msg.getFileUrl());
                break;
            case 3:
                content = new Label("[语音]");
                break;
            case 4:
                content = new Label("[视频]");
                break;
            case 5: {
                Label fileLabel = new Label("[文件] " + msg.getFileName()
                        + " (" + formatSize(msg.getFileSize()) + ")");
                fileLabel.setStyle("-fx-text-fill: blue; -fx-underline: true; -fx-cursor: hand;");
                fileLabel.setOnMouseClicked(e -> openFile(msg.getFileUrl()));
                content = fileLabel;
                break;
            }
            case 6:
                content = new Label("[位置] " + msg.getContent());
                break;
            case 7:                                            // ★ 新增
                content = buildGroupInviteNode(msg, isSelf);
                break;
            default:
                content = new Label("[未知消息]");
        }
        // 群聊消息：非自己发的，加"名字 + 角色标签"
        if (msg.getGroupId() != null && !msg.getGroupId().isEmpty() && !isSelf) {
            Label nameLabel = buildSenderLabel(msg);
            VBox wrapper = new VBox(2, nameLabel, content);
            wrapper.setAlignment(Pos.CENTER_LEFT);
            content = wrapper;   // ★ 替换 content
        }
        HBox row = new HBox(content);
        row.setStyle("-fx-padding: 4 8 4 8;");
        row.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (isSelf && msg.getMsgType() != MsgType.RECALL.getCode()) {
            ContextMenu menu = new ContextMenu();
            MenuItem recallItem = new MenuItem("撤回");

            recallItem.setOnAction(e -> {
                Long createTime = nodeTimeMap.get(row);
                long now = System.currentTimeMillis();
                long diffMs = createTime == null ? 0 : (now - createTime);

                if (createTime == null || diffMs >= RECALL_LIMIT_MS) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("提示");
                    alert.setHeaderText("无法撤回");
                    alert.setContentText("超过 2 分钟的消息不能撤回");
                    alert.show();
                    return;
                }

                Long currentMsgId = nodeMsgIdMap.get(row);
                if (currentMsgId != null && currentMsgId > 0) {
                    sendRecall(currentMsgId);
                } else {
                    addSystemMessage("[系统] 无法定位消息");
                }
            });

            menu.getItems().add(recallItem);

            row.setOnContextMenuRequested(e ->
                    menu.show(row, e.getScreenX(), e.getScreenY()));
        }

        return row;
    }
    /** 构建发送者标签：名字 + 角色徽章 */
    private Label buildSenderLabel(Message msg) {
        String name = msg.getFromUserName();
        Integer role = msg.getFromUserRole();

        String roleText = "";
        String roleColor = "";
        switch (role == null ? 3 : role) {
            case 1 -> { roleText = "[群主]"; roleColor = "#FF9800"; }
            case 2 -> { roleText = "[管理员]"; roleColor = "#2196F3"; }
            default -> { roleText = "[成员]"; roleColor = "#666666"; }        }

        Label label = new Label(roleText + " " + name);
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: "
                + (roleColor.isEmpty() ? "gray" : roleColor) + ";");
        return label;
    }
    /** 渲染群邀请消息卡片 */
    private Node buildGroupInviteNode(Message msg, boolean isSelf) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 6;");

        // 解析 content: "inviteeName:xxx"
        String content = msg.getContent();
        String inviteeName = null;
        if (content != null && content.startsWith("inviteeName:")) {
            inviteeName = content.substring("inviteeName:".length()).trim();
        }
        String groupIdStr = msg.getGroupId();

        if (inviteeName == null || groupIdStr == null || groupIdStr.isEmpty()) {
            box.getChildren().add(new Label("[群邀请消息格式异常]"));
            return box;
        }

        Label textLabel = new Label("用户「" + inviteeName + "」申请加入群 " + groupIdStr);
        box.getChildren().add(textLabel);

        // ★ 根据 inviteStatus 决定显示按钮还是静态文本
        Integer status = msg.getInviteStatus();
        if (status == null) status = 0;

        // 只有：不是自己发的 + 来自 im系统 + 未处理，才显示按钮
        boolean showButtons = !isSelf
                && "im系统".equals(msg.getFromUserName())
                && status == 0;

        if (showButtons) {
            Button agreeBtn = new Button("同意");
            Button rejectBtn = new Button("拒绝");
            agreeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            rejectBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");

            final Long groupId = Long.valueOf(groupIdStr);
            final String finalInviteeName = inviteeName;

            agreeBtn.setOnAction(e -> handleGroupInviteApprove(groupId, finalInviteeName, true, box));
            rejectBtn.setOnAction(e -> handleGroupInviteApprove(groupId, finalInviteeName, false, box));

            HBox btnBox = new HBox(10, agreeBtn, rejectBtn);
            box.getChildren().add(btnBox);

        } else if (status == 1) {
            Label doneLabel = new Label("✓ 已同意「" + inviteeName + "」入群");
            doneLabel.setStyle("-fx-text-fill: #4CAF50;");
            box.getChildren().add(doneLabel);

        } else if (status == 2) {
            Label doneLabel = new Label("✗ 已拒绝「" + inviteeName + "」入群");
            doneLabel.setStyle("-fx-text-fill: #f44336;");
            box.getChildren().add(doneLabel);

        } else if (isSelf) {
            // 自己发的（一般不会，im系统发的不会 isSelf）
            box.getChildren().add(new Label("（自己发起的邀请）"));
        }

        return box;
    }
    /** 点击同意/拒绝，调后端接口，然后替换卡片内容 */
    private void handleGroupInviteApprove(Long groupId, String inviteeName,
                                          boolean isAgree, VBox box) {
        // 禁用按钮，防止重复点击
        for (Node node : box.getChildren()) {
            if (node instanceof Button b) b.setDisable(true);
            if (node instanceof HBox hb) {
                for (Node child : hb.getChildren()) {
                    if (child instanceof Button b) b.setDisable(true);
                }
            }
        }

        new Thread(() -> {
            boolean ok = apiClient.agreeOrReject(isAgree, groupId, inviteeName);
            Platform.runLater(() -> {
                box.getChildren().clear();
                Label label;
                if (ok) {
                    label = new Label((isAgree ? "✓ 已同意" : "✗ 已拒绝")
                            + "「" + inviteeName + "」入群");
                    label.setStyle(isAgree
                            ? "-fx-text-fill: #4CAF50;"
                            : "-fx-text-fill: #f44336;");
                } else {
                    label = new Label("处理失败，可能已被其他管理员处理");
                    label.setStyle("-fx-text-fill: gray;");
                }
                box.getChildren().add(label);
            });
        }).start();
    }
    /** 发送撤回请求（单聊/群聊通用） */
    private void sendRecall(Long msgId) {
        if (isGroupChat) {
            if (currentGroupId == null) return;
        } else {
            if (currentToUser == null || currentToUser.isEmpty()) return;
        }

        if (pendingMsgIds.contains(msgId)) {
            addSystemMessage("[系统] 消息发送中，请稍后撤回");
            return;
        }

        try {
            List<Long> msgIds = List.of(msgId);
            String content = objectMapper.writeValueAsString(msgIds);

            Message msg = MessageFactory.build(MsgType.RECALL.getCode(), content);
            msg.setFromUserName(username);
            msg.setTimestamp(System.currentTimeMillis());

            if (isGroupChat) {
                msg.setGroupId(String.valueOf(currentGroupId));
            } else {
                msg.setToUserName(currentToUser);
            }

            client.send(msg);

            replaceLocalMessage(msgId, true);

        } catch (Exception e) {
            e.printStackTrace();
            addSystemMessage("[系统] 撤回失败");
        }
    }

    private void openFile(String url) {
        try {
            HostServices hostServices = ImDesktopApp.getInstance().getHostServices();
            hostServices.showDocument(url);
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setContentText("打开失败：" + e.getMessage());
            alert.show();
        }
    }

    private Node buildImageNode(String prefix, String url) {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(prefix);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(200);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-cursor: hand;");

        Image image = new Image(url, true);
        imageView.setImage(image);
        imageView.setOnMouseClicked(e -> showFullImage(url));

        image.errorProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                box.getChildren().setAll(new Label(prefix + "[图片加载失败]"));
            }
        });

        box.getChildren().addAll(label, imageView);
        return box;
    }

    private void showFullImage(String url) {
        Stage stage = new Stage();
        stage.setTitle("查看图片");

        ImageView fullView = new ImageView(new Image(url, true));
        fullView.setPreserveRatio(true);
        fullView.setFitWidth(800);
        fullView.setFitHeight(600);

        ScrollPane scrollPane = new ScrollPane(fullView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        stage.setScene(new Scene(scrollPane, 800, 600));
        stage.show();
    }

    private String formatSize(Long bytes) {
        if (bytes == null) return "未知";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
    }

    // ==================== 发送消息 ====================

    @FXML
    public void handleSend() {
        if (isGroupChat) {
            if (currentGroupId == null) {
                addSystemMessage("[系统] 未选择群");
                return;
            }
        } else {
            if (currentToUser == null || currentToUser.isEmpty()) {
                addSystemMessage("[系统] 请先选择一个好友");
                return;
            }
        }

        String content = inputField.getText().trim();
        if (content.isEmpty()) return;

        int type = isGroupChat ? MsgType.GROUP_CHAT.getCode() : MsgType.SINGLE_CHAT.getCode();
        Message msg = MessageFactory.build(type, content);
        long tempMsgId = IdUtil.getSnowflakeNextId();
        msg.setMsgId(tempMsgId);
        msg.setFromUserName(username);
        msg.setMsgType(1);
        msg.setTimestamp(System.currentTimeMillis());

        if (isGroupChat) {
            msg.setGroupId(String.valueOf(currentGroupId));
        } else {
            msg.setToUserName(currentToUser);
        }

        pendingMsgIds.add(tempMsgId);
        client.send(msg);

        Node node = buildMessageNodeAndTrack(msg, true);
        addToCurrentHistory(node);
        inputField.clear();
    }

    @FXML
    public void handleSendImage() {
        if (isGroupChat) {
            if (currentGroupId == null) {
                addSystemMessage("[系统] 未选择群");
                return;
            }
        } else {
            if (currentToUser == null || currentToUser.isEmpty()) {
                addSystemMessage("[系统] 请先选择一个好友");
                return;
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择图片");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File file = fileChooser.showOpenDialog(null);
        if (file == null) return;

        sendFileMessage(file, 2);
    }

    @FXML
    public void handleSendFile() {
        if (isGroupChat) {
            if (currentGroupId == null) {
                addSystemMessage("[系统] 未选择群");
                return;
            }
        } else {
            if (currentToUser == null || currentToUser.isEmpty()) {
                addSystemMessage("[系统] 请先选择一个好友");
                return;
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择文件");
        File file = fileChooser.showOpenDialog(null);
        if (file == null) return;

        sendFileMessage(file, 5);
    }

    /** 发送文件消息（单聊/群聊通用） */
    private void sendFileMessage(File file, int msgType) {
        String fileUrl = apiClient.uploadFile(file);
        if (fileUrl == null) {
            addSystemMessage("[系统] 文件上传失败");
            return;
        }

        Message msg = MessageFactory.build(MsgType.SINGLE_CHAT.getCode(), null);
        long tempMsgId = IdUtil.getSnowflakeNextId();
        msg.setFromUserName(username);
        msg.setMsgType(msgType);
        msg.setFileUrl(fileUrl);
        msg.setFileName(file.getName());
        msg.setFileSize(file.length());
        msg.setMimeType(probeMimeType(file));
        msg.setMsgId(tempMsgId);
        msg.setTimestamp(System.currentTimeMillis());

        if (isGroupChat) {
            msg.setGroupId(String.valueOf(currentGroupId));
        } else {
            msg.setToUserName(currentToUser);
        }

        pendingMsgIds.add(tempMsgId);
        client.send(msg);

        // 本地显示
        Message selfMsg = new Message();
        selfMsg.setMsgId(tempMsgId);
        selfMsg.setFromUserName(username);
        selfMsg.setMsgType(msgType);
        selfMsg.setFileUrl(fileUrl);
        selfMsg.setFileName(file.getName());
        selfMsg.setFileSize(file.length());
        selfMsg.setTimestamp(System.currentTimeMillis());

        if (isGroupChat) {
            selfMsg.setGroupId(String.valueOf(currentGroupId));
        } else {
            selfMsg.setToUserName(currentToUser);
        }

        Node node = buildMessageNodeAndTrack(selfMsg, true);
        addToCurrentHistory(node);
    }

    // ==================== 删除选中 ====================

    @FXML
    public void handleDeleteSelected() {
        String chatKey = currentChatKey();
        if (chatKey == null || chatKey.isEmpty()) {
            addSystemMessage("[系统] 请先选择一个聊天对象");
            return;
        }

        ObservableList<Node> history = chatHistoryMap.get(chatKey);
        if (history == null) return;

        ObservableList<Node> selected = FXCollections.observableArrayList(
                messageList.getSelectionModel().getSelectedItems());

        if (selected.isEmpty()) {
            addSystemMessage("[系统] 请先选中要删除的消息");
            return;
        }

        int count = selected.size();
        for (Node node : selected) {
            history.remove(node);
            nodeMsgIdMap.remove(node);
            nodeTimeMap.remove(node);
        }

        messageList.getSelectionModel().clearSelection();
        addSystemMessage("[系统] 已删除 " + count + " 条消息");
    }

    // ==================== 工具方法 ====================

    private void addToCurrentHistory(Node node) {
        String chatKey = currentChatKey();
        if (chatKey == null || chatKey.isEmpty()) return;

        ObservableList<Node> history = chatHistoryMap.computeIfAbsent(
                chatKey, k -> FXCollections.observableArrayList());
        history.add(node);
        messageList.scrollTo(history.size() - 1);
    }

    private void addSystemMessage(String text) {
        messageList.getItems().add(new Label(text));
        messageList.scrollTo(messageList.getItems().size() - 1);
    }

    private String probeMimeType(File file) {
        try {
            String mime = Files.probeContentType(file.toPath());
            return mime != null ? mime : "application/octet-stream";
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    // ==================== 加载历史 ====================

    @FXML
    public void handleLoadHistory() {
        String chatKey = currentChatKey();
        if (chatKey == null || chatKey.isEmpty()) {
            addSystemMessage("[系统] 请先选择一个聊天对象");
            return;
        }

        if (Boolean.FALSE.equals(hasMoreMap.get(chatKey))) {
            addSystemMessage("[系统] 没有更早的消息了");
            return;
        }

        ObservableList<Node> current = chatHistoryMap.get(chatKey);
        if (current == null || current.isEmpty()) {
            addSystemMessage("[系统] 当前没有消息");
            return;
        }

        Long cursor = null;
        for (Node node : current) {
            Long id = nodeMsgIdMap.get(node);
            if (id != null && id > 0 && (cursor == null || id < cursor)) {
                cursor = id;
            }
        }

        if (cursor == null) {
            addSystemMessage("[系统] 无法定位消息游标");
            return;
        }

        final Long lastMsgId = cursor;
        final int size = 20;
        final boolean group = isGroupChat;
        final Long groupId = currentGroupId;
        final String toUser = currentToUser;

        new Thread(() -> {
            List<JSONObject> older = group
                    ? apiClient.getGroupHistory(groupId, size, lastMsgId)
                    : apiClient.getHistory(toUser, size, lastMsgId);

            Platform.runLater(() -> {
                if (older.isEmpty()) {
                    hasMoreMap.put(chatKey, false);
                    addSystemMessage("[系统] 没有更早的消息了");
                    return;
                }

                // older 是按 msgId 倒序返回的，反转后从旧到新插入到列表头部
                for (int i = older.size() - 1; i >= 0; i--) {
                    Message msg = jsonToMessage(older.get(i));
                    boolean isSelf = username.equals(msg.getFromUserName());
                    Node node = buildMessageNodeAndTrack(msg, isSelf);
                    current.add(0, node);
                }

                if (older.size() < size) {
                    hasMoreMap.put(chatKey, false);
                }

                messageList.scrollTo(0);
            });
        }).start();
    }

    // ==================== 修改信息 / 注销 / 退出 ====================

    @FXML
    public void handleUpdateInfo() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("修改个人信息");
        dialog.setHeaderText("输入新昵称");
        dialog.setContentText("昵称：");

        dialog.showAndWait().ifPresent(nickname -> {
            boolean ok = apiClient.updatePersonalInfo(nickname, null);
            Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            alert.setContentText(ok ? "修改成功" : "修改失败");
            alert.show();
        });
    }

    @FXML
    public void handleRevoke() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("确认注销账户？注销后无法恢复");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                boolean ok = apiClient.revoke();
                Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setContentText(ok ? "注销成功" : "注销失败");
                alert.show();
                if (ok) System.exit(0);
            }
        });
    }

    @FXML
    public void handleLogout() {
        apiClient.logout();
        System.exit(0);
    }

    @FXML
    public void handleChangeStatus() {
        TextInputDialog userDialog = new TextInputDialog();
        userDialog.setHeaderText("输入要修改的用户名");
        userDialog.showAndWait().ifPresent(targetUser -> {
            TextInputDialog statusDialog = new TextInputDialog("1");
            statusDialog.setHeaderText("输入状态（1启用 0禁用）");
            statusDialog.showAndWait().ifPresent(statusStr -> {
                int status = Integer.parseInt(statusStr);
                boolean ok = apiClient.changeStatus(targetUser, status);
                Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setContentText(ok ? "修改成功" : "修改失败");
                alert.show();
            });
        });
    }

    // ==================== 群操作 ====================

    @FXML
    public void handleCreateGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("创建群");
        dialog.setHeaderText("输入群名");
        dialog.setContentText("群名：");

        dialog.showAndWait().ifPresent(groupName -> {
            if (groupName.trim().isEmpty()) {
                addSystemMessage("[系统] 群名不能为空");
                return;
            }
            new Thread(() -> {
                boolean ok = apiClient.createGroup(groupName.trim());
                Platform.runLater(() -> addSystemMessage(
                        ok ? "[系统] 创建群成功" : "[系统] 创建群失败"));
            }).start();
        });
    }

    @FXML
    public void handleInviteIntoGroup() {
        // 1. 群ID 从当前上下文拿
        Long groupId;
        if (isGroupChat && currentGroupId != null) {
            groupId = currentGroupId;
        } else {
            TextInputDialog groupIdDialog = new TextInputDialog();
            groupIdDialog.setTitle("邀请好友进群");
            groupIdDialog.setHeaderText("输入群ID");
            groupIdDialog.setContentText("群ID：");
            String str = groupIdDialog.showAndWait().orElse(null);
            if (str == null || str.trim().isEmpty()) return;
            try {
                groupId = Long.valueOf(str.trim());
            } catch (NumberFormatException e) {
                addSystemMessage("[系统] 群ID必须是数字");
                return;
            }
        }

        // 2. 拉好友列表
        new Thread(() -> {
            List<JSONObject> friends = apiClient.getFriends();
            Platform.runLater(() -> showFriendPickerDialog(friends, groupId));
        }).start();
    }

    /** 弹出好友选择对话框 */
    /** 弹出好友选择对话框 */
    private void showFriendPickerDialog(List<JSONObject> friends, Long groupId) {
        if (friends == null || friends.isEmpty()) {
            addSystemMessage("[系统] 你还没有好友，去搜索并互关吧");
            return;
        }

        // ★ 1. 拉群成员列表
        new Thread(() -> {
            List<JSONObject> members = apiClient.getGroupMembers(groupId);
            Set<String> memberSet = new HashSet<>();
            for (JSONObject m : members) {
                String u = m.getStr("username");
                if (u != null) memberSet.add(u);
            }

            Platform.runLater(() -> buildPickerUI(friends, memberSet, groupId));
        }).start();
    }

    /** 构造好友选择 UI（在 UI 线程执行） */
    private void buildPickerUI(List<JSONObject> friends, Set<String> memberSet, Long groupId) {
        ListView<HBox> listView = new ListView<>();
        Map<HBox, String> rowToUsername = new HashMap<>();

        for (JSONObject friend : friends) {
            String nickname = friend.getStr("nickname");
            String username = friend.getStr("username");
            if (username == null) continue;

            boolean alreadyIn = memberSet.contains(username);

            Label nameLabel = new Label((nickname != null && !nickname.isEmpty())
                    ? nickname + " (" + username + ")"
                    : username);
            nameLabel.setPrefWidth(220);

            HBox row = new HBox(10, nameLabel);
            row.setStyle("-fx-padding: 6; -fx-alignment: center-left;");

            // ★ 2. 已在群 → 灰显 + 禁用
            if (alreadyIn) {
                row.setDisable(true);
                row.setOpacity(0.5);
                nameLabel.setText(nameLabel.getText() + "  [已在群]");
            } else {
                rowToUsername.put(row, username);   // 只有可选的才放进 map
            }

            listView.getItems().add(row);
        }

        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Label tip = new Label("选择要邀请的好友（可多选）：");
        VBox vbox = new VBox(10, tip, listView);
        vbox.setPadding(new Insets(10));
        vbox.setPrefWidth(400);
        vbox.setPrefHeight(400);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("邀请好友进群");
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            ObservableList<HBox> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                addSystemMessage("[系统] 请至少选择一个好友");
                return;
            }

            new Thread(() -> {
                int success = 0, needApproval = 0, failed = 0;

                for (HBox row : selected) {
                    String username = rowToUsername.get(row);
                    if (username == null) continue;   // 已禁用的行跳过

                    Boolean result = apiClient.inviteIntoGroup(groupId, username);
                    if (result == null) failed++;
                    else if (result) needApproval++;
                    else success++;
                }

                final int s = success, n = needApproval, f = failed;
                Platform.runLater(() -> {
                    StringBuilder sb = new StringBuilder("[系统] 邀请完成：");
                    if (s > 0) sb.append("成功").append(s).append("人 ");
                    if (n > 0) sb.append("待审批").append(n).append("人 ");
                    if (f > 0) sb.append("失败").append(f).append("人");
                    addSystemMessage(sb.toString());
                });
            }).start();
        });
    }
    @FXML
    public void handleLeaveGroup() {
        TextField groupIdField = new TextField();
        groupIdField.setPromptText("群ID");
        if (isGroupChat && currentGroupId != null) {
            groupIdField.setText(String.valueOf(currentGroupId));
        }
        TextField usernameField = new TextField();
        usernameField.setPromptText("用户名（踢人时填，自己退群填自己）");

        VBox vbox = new VBox(10,
                new Label("群ID："), groupIdField,
                new Label("用户名："), usernameField);
        vbox.setPadding(new Insets(10));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("退群 / 踢人");
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                Long groupId = Long.valueOf(groupIdField.getText().trim());
                String username = usernameField.getText().trim();
                new Thread(() -> {
                    boolean ok = apiClient.leaveGroup(groupId, username);
                    Platform.runLater(() -> addSystemMessage(
                            ok ? "[系统] 操作成功" : "[系统] 操作失败"));
                }).start();
            } catch (Exception e) {
                addSystemMessage("[系统] 群ID必须是数字");
            }
        });
    }

    @FXML
    public void handleStopChat() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("禁言");
        dialog.setHeaderText("输入要禁言的群ID");
        dialog.setContentText("群ID：");
        if (isGroupChat && currentGroupId != null) {
            dialog.getEditor().setText(String.valueOf(currentGroupId));
        }

        dialog.showAndWait().ifPresent(str -> {
            try {
                Long groupId = Long.valueOf(str.trim());
                new Thread(() -> {
                    boolean ok = apiClient.stopChat(groupId);
                    Platform.runLater(() -> addSystemMessage(
                            ok ? "[系统] 已禁言" : "[系统] 禁言失败"));
                }).start();
            } catch (NumberFormatException e) {
                addSystemMessage("[系统] 群ID必须是数字");
            }
        });
    }

    @FXML
    public void handleCancelStopChat() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("取消禁言");
        dialog.setHeaderText("输入要取消禁言的群ID");
        dialog.setContentText("群ID：");
        if (isGroupChat && currentGroupId != null) {
            dialog.getEditor().setText(String.valueOf(currentGroupId));
        }

        dialog.showAndWait().ifPresent(str -> {
            try {
                Long groupId = Long.valueOf(str.trim());
                new Thread(() -> {
                    boolean ok = apiClient.cancelStopChat(groupId);
                    Platform.runLater(() -> addSystemMessage(
                            ok ? "[系统] 已取消禁言" : "[系统] 取消失败"));
                }).start();
            } catch (NumberFormatException e) {
                addSystemMessage("[系统] 群ID必须是数字");
            }
        });
    }

    @FXML
    public void handleSetMemberRole() {
        TextField groupIdField = new TextField();
        groupIdField.setPromptText("群ID");
        if (isGroupChat && currentGroupId != null) {
            groupIdField.setText(String.valueOf(currentGroupId));
        }
        TextField usernameField = new TextField();
        usernameField.setPromptText("要设置的用户名");

        VBox vbox = new VBox(10,
                new Label("群ID："), groupIdField,
                new Label("用户名："), usernameField);
        vbox.setPadding(new Insets(10));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("设置成员角色");
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                Long groupId = Long.valueOf(groupIdField.getText().trim());
                String username = usernameField.getText().trim();
                new Thread(() -> {
                    boolean ok = apiClient.setMemberRole(username, groupId);
                    Platform.runLater(() -> addSystemMessage(
                            ok ? "[系统] 设置成功" : "[系统] 设置失败"));
                }).start();
            } catch (Exception e) {
                addSystemMessage("[系统] 群ID必须是数字");
            }
        });
    }
}