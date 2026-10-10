package com.yxlogin.service;

import com.yxlogin.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 消息表 服务类
 * </p>
 *
 * @author yu
 * @since 2026-09-25
 */
public interface ChatMessageService extends IService<ChatMessage> {

    void readMessage(String fromUserName);

    List<ChatMessage> getRecent(String fromUserName);

    List<ChatMessage> getHistory(String fromUserName,int size,Long lastMsgId);

    void recallMessage(List<Long> msgIds,String username);

    List<ChatMessage> getGroupRecent(Long groupId);

    List<ChatMessage> getGroupHistory(Long groupId, int size, Long lastMsgId);
}
