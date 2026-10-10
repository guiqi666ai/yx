package com.yxlogin.service;

import com.yxlogin.entity.ChatGroup;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 聊天群表 服务类
 * </p>
 *
 * @author yu
 * @since 2026-10-03
 */
public interface ChatGroupService extends IService<ChatGroup> {

    void createGroup(String groupName);
}
