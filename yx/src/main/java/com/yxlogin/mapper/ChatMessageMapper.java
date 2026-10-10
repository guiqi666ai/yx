package com.yxlogin.mapper;

import com.yxlogin.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 消息表 Mapper 接口
 * </p>
 *
 * @author yu
 * @since 2026-09-19
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("select * from chat_message where to_user_name=#{toUserName} and status=0")
    List<ChatMessage> selectByToUserId(@Param("toUserName")String toUserName);
}
