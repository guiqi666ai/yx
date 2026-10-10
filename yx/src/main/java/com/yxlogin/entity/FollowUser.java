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
 * 用户关注表
 * </p>
 *
 * @author yu
 * @since 2026-09-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("follow_user")
public class FollowUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 关注者
     */
    private String fromUserName;

    /**
     * 被关注者
     */
    private String toUserName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 0未互关 1已互关
     */
    private Integer isMutual;


}
