package com.yx.entity;

import lombok.Data;

@Data
public class Message {
    private int magic;
    private int version;
    private int type;
    private long msgId;
    private String fromUserName;
    private String toUserName;
    private String groupId;
    private long timestamp;
    private String token;
    private Integer inviteStatus;
    /** 发送者在群里的角色：1 群主 / 2 管理员 / 3 普通成员，单聊时为 null */
    private Integer fromUserRole;
    // 消息内容
    private int msgType;        // 1文本 2图片 3语音 4视频 5文件 6位置
    private String content;     // 文本内容
    private String fileUrl;     // 文件URL
    private String fileName;    // 文件名
    private Long fileSize;      // 文件大小
    private String mimeType;    // MIME类型
}
