package com.yxlogin.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxlogin.entity.ChatMessage;
import com.yxlogin.entity.GroupInviteRequest;
import com.yxlogin.entity.GroupMember;
import com.yxlogin.key.ChatKey;
import com.yxlogin.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yxlogin.mapper.GroupInviteRequestMapper;
import com.yxlogin.mapper.GroupMemberMapper;
import com.yxlogin.mapper.UserMapper;
import com.yxlogin.service.ChatMessageService;
import com.yxlogin.service.GroupMemberService;
import com.yxlogin.util.SingleChatKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 消息表 服务实现类
 * </p>
 *
 * @author yu
 * @since 2026-09-25
 */
@Service
@Slf4j
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private GroupInviteRequestMapper groupInviteRequestMapper;
    @Resource
    private SingleChatKeyUtil singleChatKeyUtil;
    @Resource
    private GroupMemberMapper groupMemberMapper;
    @Override
    public void readMessage(String fromUserName) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.valueOf(userIdStr);
        String username = userMapper.selectById(userId).getUsername();
        String read_key="read:"+fromUserName+"->"+username;
        RLock lock = redissonClient.getLock(read_key);
        try {
            boolean tryLock = lock.tryLock(3, TimeUnit.SECONDS);
            if(!tryLock){
                throw new RuntimeException("操作频繁");
            }
            lambdaUpdate().eq(ChatMessage::getFromUserName,fromUserName)
                    .eq(ChatMessage::getToUserName,username)
                    .eq(ChatMessage::getStatus,0)
                    .set(ChatMessage::getStatus,1).update();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            if(lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

    @Override
    public List<ChatMessage> getRecent(String fromUserName) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.valueOf(userIdStr);
        String username = userMapper.selectById(userId).getUsername();

        // ✅ 只生成1个key，不用查两个
        String chatKey = singleChatKeyUtil.getChatKey(username, fromUserName);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().rangeWithScores(chatKey, 0, 49);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            try {
                ChatMessage cm = objectMapper.readValue(tuple.getValue(), ChatMessage.class);
                result.add(cm);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        // 批量填充邀请状态
        fillInviteStatus(result,username);

        return result;
    }

    @Override
    public List<ChatMessage> getHistory(String fromUserName, int size, Long lastMsgId) {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.valueOf(userIdStr);
        String username = userMapper.selectById(userId).getUsername();

        List<ChatMessage> list = lambdaQuery()
                .and(w -> w.eq(ChatMessage::getFromUserName, fromUserName)
                        .eq(ChatMessage::getToUserName, username))
                .or(w -> w.eq(ChatMessage::getFromUserName, username)
                        .eq(ChatMessage::getToUserName, fromUserName))
                .lt(lastMsgId != null, ChatMessage::getMsgId, lastMsgId)
                .orderByDesc(ChatMessage::getMsgId)
                .last("LIMIT " + size)
                .list();

        if (list == null || list.isEmpty()) {
            return List.of();
        }

        // 批量填充邀请状态
        fillInviteStatus(list,username);

        return list;
    }
    /**
     * 批量给 msgType=7 的消息填充 inviteStatus。
     * 只发一次 SQL，避免 N+1。
     */
    private void fillInviteStatus(List<ChatMessage> messages,String username) {
        if (CollUtil.isEmpty(messages)) return;

        // 1. 收集所有群邀请的 (groupId, inviteeName)
        List<InviteKey> keys = new ArrayList<>();
        for (ChatMessage cm : messages) {
            if (cm.getMsgType() != null && cm.getMsgType() == 7) {
                InviteKey k = InviteKey.of(cm);
                if (k != null) keys.add(k);
            }
        }
        if (keys.isEmpty()) return;

        // 2. 去重
        Set<Long> groupIds = keys.stream().map(InviteKey::groupId).collect(Collectors.toSet());
        Set<String> inviteeNames = keys.stream().map(InviteKey::inviteeName).collect(Collectors.toSet());

        // 3. 一次 SQL 查所有
        List<GroupInviteRequest> requests = groupInviteRequestMapper.selectList(
                new LambdaQueryWrapper<GroupInviteRequest>()
                        .eq(GroupInviteRequest::getHandlerUsername,username)
                        .in(GroupInviteRequest::getGroupId, groupIds)
                        .in(GroupInviteRequest::getInviteeUsername, inviteeNames)
                        .orderByDesc(GroupInviteRequest::getId));

        // 4. 构建 Map<groupId_inviteeName, status>
        Map<String, Integer> statusMap = new HashMap<>();
        for (GroupInviteRequest r : requests) {
            String key = r.getGroupId() + "_" + r.getInviteeUsername();
            Integer status = r.getStatus() == null ? 0 : r.getStatus();
            statusMap.putIfAbsent(key, status);   // 因为已按 id 倒序，保留最新的
        }

        // 5. 回填
        for (ChatMessage cm : messages) {
            if (cm.getMsgType() != null && cm.getMsgType() == 7) {
                InviteKey k = InviteKey.of(cm);
                if (k != null) {
                    cm.setInviteStatus(statusMap.getOrDefault(k.toKey(), 0));
                }
            }
        }
    }

    /** 群邀请的唯一标识 */
    private record InviteKey(Long groupId, String inviteeName) {
        static InviteKey of(ChatMessage cm) {
            String content = cm.getContent();
            String groupIdStr = cm.getGroupId();
            if (content == null || !content.startsWith("inviteeName:")
                    || groupIdStr == null || groupIdStr.isEmpty()) {
                return null;
            }
            try {
                String inviteeName = content.substring("inviteeName:".length()).trim();
                return new InviteKey(Long.valueOf(groupIdStr), inviteeName);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String toKey() { return groupId + "_" + inviteeName; }
    }

    @Override
    @Transactional
    public void recallMessage(List<Long> msgIds,String username) {
        List<ChatMessage> chatMessages = baseMapper.selectByIds(msgIds);
        baseMapper.update(null,new LambdaUpdateWrapper<ChatMessage>()
                .ne(ChatMessage::getToUserName,username)
                        .set(ChatMessage::getStatus,2)
                .in(ChatMessage::getMsgId,msgIds));
        for (ChatMessage chatMessage : chatMessages) {
            String toUserName = chatMessage.getToUserName();
            if(toUserName.equals(username)){
                log.error("不能撤回对方消息");
                continue;
            }
            String key = ChatKey.CHAT_KEY.formatted(username, toUserName);
            try {
                chatMessage.setStatus(0);//为了保证与redis的数据里的字段status值一致才改的
                chatMessage.setCreateTime(null);//这个也是
                String chatMessageStr = objectMapper.writeValueAsString(chatMessage);
                Double rowTime = stringRedisTemplate.opsForZSet().score(key, chatMessageStr);
                long now = System.currentTimeMillis();

                if(now-rowTime>2*1000*60){
                    log.error("两分钟之后的消息不能撤回");
                    continue;
                }
                Long remove = stringRedisTemplate.opsForZSet().remove(key, chatMessageStr);
                if(remove!=0) log.info("撤回消息成功");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        }
    }

    @Override
    public List<ChatMessage> getGroupRecent(Long groupId) {
        String groupKey = "groupId:" + groupId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .rangeWithScores(groupKey, 0, 49);
        if(typedTuples==null||CollUtil.isEmpty(typedTuples)) return List.of();
        List<ChatMessage> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            try {
                ChatMessage cm = objectMapper.readValue(tuple.getValue(), ChatMessage.class);
                result.add(cm);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    /**
     * 分页拉群聊历史
     * @param groupId 群ID
     * @param size 每页条数
     * @param lastMsgId 游标（上一页最早一条的 msgId），首次传 null
     */
    public List<ChatMessage> getGroupHistory(Long groupId, int size, Long lastMsgId) {
        List<ChatMessage> list = lambdaQuery()
                .eq(ChatMessage::getGroupId, String.valueOf(groupId))
                .lt(lastMsgId != null, ChatMessage::getMsgId, lastMsgId)
                .orderByDesc(ChatMessage::getMsgId)
                .last("LIMIT " + size)
                .list();

        if (list == null || list.isEmpty()) {
            return List.of();
        }

        // ★ 补发送者角色
        List<GroupMember> members = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getStatus, 1));
        Map<String, Integer> roleMap = members.stream()
                .collect(Collectors.toMap(GroupMember::getUsername, GroupMember::getRole));

        for (ChatMessage cm : list) {
            cm.setFromUserRole(roleMap.getOrDefault(cm.getFromUserName(), 3));
        }

        return list;
    }
}
