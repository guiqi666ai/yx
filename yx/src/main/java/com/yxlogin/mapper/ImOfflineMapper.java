package com.yxlogin.mapper;

import com.yxlogin.entity.ImOffline;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 离线消息表 Mapper 接口
 * </p>
 *
 * @author yu
 * @since 2026-09-18
 */
public interface ImOfflineMapper extends BaseMapper<ImOffline> {
    @Select("select * from im_offline where to_user_name=#{userName}")
    List<ImOffline> selectByUserName(@Param("userName") String userName);
    @Delete("DELETE FROM im_offline WHERE to_user_name = #{userName}")
    int deleteByUserName(@Param("userName")String userName);
}