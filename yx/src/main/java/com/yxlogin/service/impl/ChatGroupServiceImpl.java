package com.yxlogin.service.impl;

import com.yxlogin.entity.ChatGroup;
import com.yxlogin.entity.GroupMember;
import com.yxlogin.mapper.ChatGroupMapper;
import com.yxlogin.mapper.GroupMemberMapper;
import com.yxlogin.mapper.UserMapper;
import com.yxlogin.service.ChatGroupService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxlogin.service.IdSegmentService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 聊天群表 服务实现类
 * </p>
 *
 * @author yu
 * @since 2026-10-03
 */
@Service
@Slf4j
public class ChatGroupServiceImpl extends ServiceImpl<ChatGroupMapper, ChatGroup> implements ChatGroupService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private IdSegmentService segmentService;
    @Resource
    private GroupMemberMapper groupMemberMapper;
    @PostConstruct
    private void init(){
        segmentService.createSegment("chatGroup",0L,2);
        segmentService.createSegment("groupMember",0L,2);
        segmentService.createSegment("groupInviteRequest",0L,2);
    }
    @Override
    @Transactional
    public void createGroup(String groupName) {

        String userId =(String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userMapper.selectById(userId).getUsername();
        long chatGroupId = segmentService.nextId("chatGroup");
        ChatGroup chatGroup = new ChatGroup().setGroupName(groupName)
                .setOwnerName(username)
                .setId(chatGroupId);
        save(chatGroup);
        groupMemberMapper.insert(new GroupMember().setGroupId(chatGroupId)
                .setRole(1)
                .setStatus(1)
                .setUsername(username)
                        .setId(segmentService.nextId("groupMember"))
                );
    }
}
