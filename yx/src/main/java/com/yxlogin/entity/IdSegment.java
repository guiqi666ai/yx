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
 * 
 * </p>
 *
 * @author yu
 * @since 2026-09-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("id_segment")
public class IdSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务标识
     */
    @TableId(value = "biz_tag", type = IdType.ASSIGN_ID)
    private String bizTag;

    /**
     * 已分配的最大ID
     */
    @TableField("max_id")
    private Long maxId;

    /**
     * 号段步长
     */
    @TableField("step")
    private Integer step;

    /**
     * 乐观锁版本
     */
    @TableField("version")
    private Long version;

    @TableField("update_time")
    private LocalDateTime updateTime;


}
