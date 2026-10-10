package com.yxlogin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 消息表
 * </p>
 *
 * @author yu
 * @since 2026-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（号段生成）
     */
    @TableId(value = "msg_id", type = IdType.ASSIGN_ID)
    private Long msgId;

    /**
     * 发送者
     */
    private String fromUserName;

    /**
     * 接收者（单聊）
     */
    private String toUserName;

    /**
     * 群ID（群聊）
     */
    private String groupId;

    /**
     * 1文本 2图片 3语音 4视频 5文件 6位置 7系统消息
     */
    private Integer msgType;

    /**
     * 文本内容（msg_type=1）
     */
    private String content;

    /**
     * 文件URL（msg_type=2~5）
     */
    private String fileUrl;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型，如 image/jpeg
     */
    private String mimeType;

    /**
     * 0未读 1已读 2撤回
     */
    private Integer status;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private Integer inviteStatus;
    @TableField(exist = false)
    private Integer fromUserRole;   // ★ 新增：1群主 2管理员 3普通成员
}
