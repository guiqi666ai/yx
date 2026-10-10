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
 * 群成员表
 * </p>
 *
 * @author yu
 * @since 2026-10-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("group_member")
public class GroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 群id
     */
    private Long groupId;

    /**
     * 成员用户名
     */
    private String username;

    /**
     * 1群主 2管理员 3普通成员
     */
    private Integer role;

    /**
     * 0已退出 1正常 2禁言
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
