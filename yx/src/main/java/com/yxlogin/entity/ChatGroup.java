package com.yxlogin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 聊天群表
 * </p>
 *
 * @author yu
 * @since 2026-10-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_group")
public class ChatGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群id
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 群名
     */
    private String groupName;

    /**
     * 群主用户名
     */
    private String ownerName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
