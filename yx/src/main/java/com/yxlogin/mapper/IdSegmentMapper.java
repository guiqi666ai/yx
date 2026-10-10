package com.yxlogin.mapper;

import com.yxlogin.entity.IdSegment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author yu
 * @since 2026-09-16
 */
public interface IdSegmentMapper extends BaseMapper<IdSegment> {
    IdSegment selectByBizTag(@Param("biz_tag") String bizTag);
    int updateMaxId(@Param("version") long version,
                    @Param("biz_tag") String bizTag,
                    @Param("step") int step);
}
