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
 * 群邀请申请表
 * </p>
 *
 * @author yu
 * @since 2026-10-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("group_invite_request")
public class GroupInviteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 申请ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 群ID
     */
    private Long groupId;

    /**
     * 被邀请人
     */
    private String inviteeUsername;

    /**
     * 发起邀请的人
     */
    private String inviterUsername;

    /**
     * 0待处理 1已同意 2已拒绝 3已过期
     */
    private Integer status;

    /**
     * 处理人
     */
    private String handlerUsername;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
