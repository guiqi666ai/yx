package com.yxlogin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yxlogin.entity.ChatGroup;
import com.yxlogin.entity.FollowUser;
import com.yxlogin.entity.GroupInviteRequest;
import com.yxlogin.entity.GroupMember;
import com.yxlogin.handler.ImServerHandler;
import com.yxlogin.mapper.*;
import com.yxlogin.service.GroupMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxlogin.service.IdSegmentService;
import com.yxlogin.vo.GroupVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 群成员表 服务实现类
 * </p>
 *
 * @author yu
 * @since 2026-10-03
 */
@Service
@Slf4j
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements GroupMemberService {
    @Resource
    private FollowUserMapper followUserMapper;
    @Resource
    private IdSegmentService idSegmentService;
    @Resource
    private UserMapper userMapper;
    @Resource
    private ImServerHandler imServerHandler;
    @Resource
    private GroupInviteRequestMapper groupInviteRequestMapper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ChatGroupMapper chatGroupMapper;

    @Override
    @Transactional
    public boolean inviteIntoGroup(Long groupId, String inviteeName) {
        GroupMember targetGroupMember = lambdaQuery().eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUsername, inviteeName).one();
        if(targetGroupMember!=null) throw new RuntimeException("该成员已在群里不能邀请");
        Long count = lambdaQuery().eq(GroupMember::getGroupId, groupId).count();
        if(count>=500){
            throw new RuntimeException("群已满不可邀请人");
        }
        String userId =(String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String invitorName= userMapper.selectById(userId).getUsername();        //只有互相关注才可以邀请
        List<FollowUser> followUsers = followUserMapper.selectList(new LambdaQueryWrapper<FollowUser>()
                .and(w -> w.eq(FollowUser::getToUserName, inviteeName)
                        .eq(FollowUser::getFromUserName, invitorName))
                .or(w -> w.eq(FollowUser::getToUserName, invitorName)
                        .eq(FollowUser::getFromUserName, inviteeName))
        );
        if(CollUtil.isEmpty(followUsers)||followUsers.size()!=2){
            throw new RuntimeException("只能邀请好友进群");
        }
        Optional<GroupMember> groupMemberOptional = lambdaQuery().eq(GroupMember::getUsername, invitorName)
                .eq(GroupMember::getGroupId, groupId).oneOpt();

        if(groupMemberOptional.isEmpty()){
            throw new RuntimeException("没有查到该群:"+groupId+"信息");
        }
        GroupMember groupMember = groupMemberOptional.get();
        Integer role = groupMember.getRole();
        //普通成员邀请要经过管理员同意才能直接入群
        if(role==3){
            //发消息给管理员
            GroupInviteRequest groupInviteRequest = new GroupInviteRequest().setGroupId(groupId)
                    .setId(idSegmentService.nextId("groupInviteRequest"))
                    .setInviterUsername(invitorName)
                    .setStatus(0)
                    .setInviteeUsername(inviteeName);
            groupInviteRequestMapper.insert(groupInviteRequest);
            List<String> usernameList = lambdaQuery().eq(GroupMember::getGroupId, groupId)
                    .in(GroupMember::getRole, 1, 2)
                    .eq(GroupMember::getStatus, 1)
                    .select(GroupMember::getUsername)
                    .list().stream()
                    .map(GroupMember::getUsername)
                    .toList();
            imServerHandler.pushGroupInviteRequest(usernameList,groupId,inviteeName);
            return true;
        }
        //管理员或群主邀请可以直接入群
        try {
            GroupMember member = new GroupMember().setGroupId(groupId)
                    .setRole(3)
                    .setUsername(inviteeName)
                    .setId(idSegmentService.nextId("groupMember"))
                    .setStatus(1);
            save(member);
        } catch (Exception e) {
            throw new RuntimeException("邀请好友进群失败",e);
        }
        return false;
    }

    @Override
    public void leaveGroup(Long groupId,String username) {
        String userId =(String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername= userMapper.selectById(userId).getUsername();
        Optional<GroupMember> optionalGroupMember = lambdaQuery().eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUsername, currentUsername).oneOpt();
        if(optionalGroupMember.isEmpty()){
            throw new RuntimeException("你不在该群里，不可操作");
        }
        Integer role = optionalGroupMember.get().getRole();
        //是不是当前用户自己操作
        if(username.equals(currentUsername)){
            if (role==1) throw new RuntimeException("群主只能先转移群主身份才能退群");
            try {
                lambdaUpdate().eq(GroupMember::getGroupId,groupId)
                        .eq(GroupMember::getUsername,username)
                        .set(GroupMember::getStatus,0)
                        .update();
            } catch (Exception e) {
                throw new RuntimeException("自己退群操作失败",e);
            }
        }
        //不是自己操作
        else {

            Optional<GroupMember> optionalTargetGroupMember = lambdaQuery().eq(GroupMember::getGroupId, groupId)
                    .eq(GroupMember::getUsername, username).oneOpt();
            if(optionalTargetGroupMember.isEmpty()){
                throw new RuntimeException("要踢出群的用户不存在");
            }
            Integer targetRole = optionalTargetGroupMember.get().getRole();
            if(targetRole==1) throw new RuntimeException("群主不能被踢出");
            if(role==3){
                throw new RuntimeException("你没有权利踢人");
            }else {
                try {
                    if(role==2&&targetRole==2) throw new RuntimeException("管理员只能被群主踢出群");
                    lambdaUpdate().eq(GroupMember::getGroupId,groupId)
                            .eq(GroupMember::getUsername,username)
                            .set(GroupMember::getStatus,0)
                            .update();
                } catch (Exception e) {
                    throw new RuntimeException("踢人操作失败",e);
                }
            }
        }

    }

    @Override
    public void stopChat(Long groupId) {
        unStopOrStopChat(groupId,2,1);
    }

    @Override
    public void cancelStopChat(Long groupId) {
        unStopOrStopChat(groupId,1,2);
    }

    @Override
    public void setMemberRole(String toUsername,Long groupId) {
        String userId =(String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername= userMapper.selectById(userId).getUsername();
        Optional<GroupMember> optionalGroupMember = lambdaQuery().eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUsername, currentUsername).oneOpt();
        if(optionalGroupMember.isEmpty()){
            throw new RuntimeException("你不在该群里，不可操作");
        }
        Integer role = optionalGroupMember.get().getRole();
        if (role!=1) throw new RuntimeException("你不是群主，无权改变其他成员角色");
        try {
            lambdaUpdate().eq(GroupMember::getUsername,toUsername)
                    .eq(GroupMember::getGroupId,groupId)
                    .setSql(" role = case " +
                            " when role= 3 then 2" +
                            " when role= 2 then 3" +
                            " else role end").update();
        } catch (Exception e) {
            throw new RuntimeException("修改成员角色失败",e);
        }
    }

    @Override
    public void agreeOrReject(boolean isAgree, Long groupId, String inviteeName) {
        String userId =(String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String handlerUsername= userMapper.selectById(userId).getUsername();
        List<GroupInviteRequest> groupInviteRequests = groupInviteRequestMapper.selectByMap(Map.of("group_id", groupId,
                "invitee_username", inviteeName));
        if(groupInviteRequests==null||CollUtil.isEmpty(groupInviteRequests)) throw new RuntimeException("未查到该申请信息");
        Integer status = groupInviteRequests.getFirst().getStatus();
        if(status!=0) throw new RuntimeException("该申请已被处理或过期");
        RLock lock = redissonClient.getLock(groupId+inviteeName);
        try {
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if(!acquired) throw new RuntimeException("操作频繁，请稍后重试");
            GroupMemberServiceImpl proxy = (GroupMemberServiceImpl) AopContext.currentProxy();
            if(!isAgree) {
                proxy.agreeOrRejectMethod(groupId, inviteeName,2,handlerUsername);
            }else {
                proxy.agreeOrRejectMethod(groupId, inviteeName,1,handlerUsername);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }finally {
            if(lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void agreeOrRejectMethod(Long groupId, String inviteeName,Integer status,String handlerUsername){
        groupInviteRequestMapper.update(new LambdaUpdateWrapper<GroupInviteRequest>().eq(GroupInviteRequest::getGroupId,groupId)
                .eq(GroupInviteRequest::getInviteeUsername,inviteeName)
                .set(GroupInviteRequest::getStatus,status)
                .set(GroupInviteRequest::getHandlerUsername,handlerUsername));
        if (status==1) {
            GroupMember member = new GroupMember().setGroupId(groupId)
                    .setRole(3)
                    .setUsername(inviteeName)
                    .setId(idSegmentService.nextId("groupMember"))
                    .setStatus(1);
            save(member);
        }
    }
    @Override
    public List<GroupVo> getMyGroups() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = userMapper.selectById(userId).getUsername();

        // 1. 查我加入的群
        List<GroupMember> myGroups = lambdaQuery()
                .eq(GroupMember::getUsername, username)
                .eq(GroupMember::getStatus, 1)
                .list();
        if(myGroups==null||CollUtil.isEmpty(myGroups)) return List.of();
        List<GroupVo> result = new ArrayList<>();
        for (GroupMember gm : myGroups) {
            ChatGroup group = chatGroupMapper.selectById(gm.getGroupId());
            if (group == null) continue;

            Long count = lambdaQuery()
                    .eq(GroupMember::getGroupId, gm.getGroupId())
                    .eq(GroupMember::getStatus, 1)
                    .count();

            GroupVo vo = new GroupVo();
            vo.setGroupId(gm.getGroupId());
            vo.setGroupName(group.getGroupName());
            vo.setMemberCount(count == null ? 0 : count.intValue());
            vo.setMyRole(gm.getRole());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getGroupMembers(Long groupId, int i) {
        List<GroupMember> members = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getStatus, i)   // 只查正常的
                .list();
        List<Map<String, Object>> result = members.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("username", m.getUsername());
            map.put("role", m.getRole());        // 1 群主 / 2 管理员 / 3 普通
            return map;
        }).toList();
        return result;
    }


    private void unStopOrStopChat(Long groupId, Integer status,Integer rowStatues) {
        String userId =(String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername= userMapper.selectById(userId).getUsername();
        Optional<GroupMember> optionalGroupMember = lambdaQuery().eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUsername, currentUsername).oneOpt();
        if(optionalGroupMember.isEmpty()){
            throw new RuntimeException("你不在该群里，不可操作");
        }
        Integer role = optionalGroupMember.get().getRole();
        if(role==3) throw new RuntimeException("普通用户不可以执行禁言");
        try {
            lambdaUpdate().eq(GroupMember::getGroupId, groupId)
                    .eq(GroupMember::getRole,3)
                    .eq(GroupMember::getStatus,rowStatues)
                    .set(GroupMember::getStatus,status)
                    .update();
        } catch (Exception e) {
            throw new RuntimeException("禁言操作失败",e);
        }
    }
}
