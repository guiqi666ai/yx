package com.yxlogin.handler;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.entity.Message;
import com.yx.entity.MessageFactory;
import com.yx.entity.MsgType;
import com.yxlogin.entity.ChatMessage;
import com.yxlogin.entity.GroupMember;
import com.yxlogin.entity.ImOffline;
import com.yxlogin.mapper.ChatMessageMapper;
import com.yxlogin.mapper.GroupMemberMapper;
import com.yxlogin.mapper.ImOfflineMapper;
import com.yxlogin.service.ChatMessageService;
import com.yxlogin.service.IdSegmentService;
import com.yxlogin.util.JwtUtil;
import com.yxlogin.util.SingleChatKeyUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@ChannelHandler.Sharable//这个 Handler 可以被多个 Channel（连接）共享
public class ImServerHandler extends SimpleChannelInboundHandler<Message> {
    private static final String USER_CHANNEL_KEY="channel:";
    @Value("${server.port}")
    private String port;
    private static final AttributeKey<String> USER_NAME =AttributeKey.valueOf("userName");
    //用于一个账号只能在一个地方登录
    private static final Map<String, Channel> ONLINE_USERS=new ConcurrentHashMap<>();
    @Resource
    private IdSegmentService idSegmentService;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private ImOfflineMapper offlineMapper;
    @Resource
    private ExecutorService userExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RestClient restClient;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ChatMessageService chatMessageService;
    @Resource
    private SingleChatKeyUtil singleChatKeyUtil;
    @Resource
    private GroupMemberMapper groupMemberMapper;
    /**
     * 消息来了后怎么处理
     */
    @PostConstruct
    private void init(){
        idSegmentService.createSegment("message",0L,2);
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        if(!validate(ctx,msg)) return;
        int code = msg.getType();
        MsgType msgType = MsgType.fromCode(code);
        switch (msgType){
            case LOGIN -> handleLogin(ctx,msg);
            case SINGLE_CHAT -> handleChat(ctx,msg);
            case HEARTBEAT -> handleHeartbeat(ctx,msg);
            case RECALL -> handleRecall(ctx,msg);
            case GROUP_CHAT-> handleGroupChat(ctx,msg);
            default -> log.error("未处理消息类型:{}",msgType);
        }
    }




    private boolean validate(ChannelHandlerContext ctx, Message msg) {
        if(msg.getMagic()!= MessageFactory.magic){
            ctx.close();
            return false;
        }
        if(msg.getVersion()!=MessageFactory.version){
            ctx.close();
            return false;
        }
        MsgType msgType = MsgType.fromCode(msg.getType());
        if(msgType==null){
            ctx.close();
            return false;
        }
        if(msgType!=MsgType.LOGIN&&ctx.channel().attr(USER_NAME).get()==null) {
            ctx.writeAndFlush(MessageFactory.buildError("未登录请先登录"));
            ctx.close();
            return false;
        }
        return true;
    }
    private void handleLogin(ChannelHandlerContext ctx, Message msg) {
        String token = msg.getToken();
        boolean verify = jwtUtil.isVerify(token);
        if(!verify){
            ctx.writeAndFlush(MessageFactory.buildError("token无效"));
            ctx.close();
            return;
        }
        String username = msg.getFromUserName();
        Channel oldChannel = ONLINE_USERS.put(username, ctx.channel());
        if(oldChannel!=null&&oldChannel!=ctx.channel()){
            //提示之前的那条连接的客户端
            oldChannel.writeAndFlush(MessageFactory.buildError("账号已在别处登录"));
            oldChannel.close();
        }
        ctx.channel().attr(USER_NAME).set(username);
        ctx.writeAndFlush(MessageFactory.build(MsgType.LOGIN_RESP.getCode(), "登录成功"));

        String key = USER_CHANNEL_KEY + username;
        stringRedisTemplate.opsForValue().set(key,"http://127.0.0.1:"+port,60,TimeUnit.SECONDS);
        log.info("用户上线 userId={}",username);
        //一个 ctx 对应一个 handler 在 pipeline 里的位置。多个线程同时操作同一个 ctx，会破坏 handler 的状态
        //channel是全局共享的
        //所以这个传channel而不是ctx
        //userExecutor.submit(()->pushOfflineMessages(ctx.channel(),username));
        userExecutor.submit(()->deleteOfflineMessages(username));
    }

    private void deleteOfflineMessages(String username) {
        List<ImOffline> imOffLines = offlineMapper.selectByUserName(username);
        if (CollUtil.isEmpty(imOffLines)) {
            return;
        }
        List<Long> msgIds = imOffLines.stream()
                .map(ImOffline::getId)
                .collect(Collectors.toList());
        try {
            offlineMapper.deleteByIds(msgIds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void pushOfflineMessages(Channel channel, String username) {
        try {
            // 1. 查离线消息队列
            List<ImOffline> imOffLines = offlineMapper.selectByUserName(username);
            if (CollUtil.isEmpty(imOffLines)) {
                return;
            }

            // 2. 批量查出对应的 chat_message，构建 msgId → ChatMessage 的映射
            List<Long> msgIds = imOffLines.stream()
                    .map(ImOffline::getMsgId)
                    .collect(Collectors.toList());

            List<ChatMessage> chatMessages = chatMessageMapper.selectBatchIds(msgIds);
            Map<Long, ChatMessage> chatMessageMap = chatMessages.stream()
                    .collect(Collectors.toMap(ChatMessage::getMsgId, c -> c));

            // 3. 逐条推送，推送成功的记入 toDelete
            List<Long> toDelete = new ArrayList<>();
            for (ImOffline imOffLine : imOffLines) {
                Long msgId = imOffLine.getMsgId();
                ChatMessage chatMessage = chatMessageMap.get(msgId);
                if (chatMessage == null) {
                    log.warn("离线消息对应的 chat_message 不存在, msgId={}", msgId);
                    continue;   // 不加入 toDelete，下次登录还会再查一次
                }
                try {
                    Message message = toMessage(chatMessage);
                    channel.writeAndFlush(message);
                    toDelete.add(imOffLine.getId());
                } catch (Exception e) {
                    log.error("推送离线消息失败, msgId={}", msgId, e);
                    // 不加入 toDelete，下次重推
                }
            }

            // 4. 只删推送成功的
            if (!toDelete.isEmpty()) {
                offlineMapper.deleteByIds(toDelete);
            }

        } catch (Exception e) {
            log.error("推送离线消息失败，username:{}", username, e);
        }
    }

    private Message toMessage(ChatMessage chatMessage) {
        Message message = new Message();
        message.setMagic(MessageFactory.magic);
        message.setVersion(MessageFactory.version);
        message.setMsgId(chatMessage.getMsgId());
        message.setFromUserName(chatMessage.getFromUserName());
        message.setToUserName(chatMessage.getToUserName());
        message.setContent(chatMessage.getContent());
        message.setTimestamp(chatMessage.getCreateTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        message.setMsgType(chatMessage.getMsgType());
        message.setType(MsgType.SINGLE_CHAT.getCode()); // 统一走单聊
        message.setGroupId(chatMessage.getGroupId());

        return message;
    }
    //这个ctx是当前消息来源的连接，不是发送到其他地方的连接
    public void handleChat(ChannelHandlerContext ctx, Message msg) {
        long rowMsgId = msg.getMsgId();
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent("msg:" + rowMsgId, "1", 5, TimeUnit.MINUTES);
        if(!Boolean.TRUE.equals(ifAbsent)){
            log.warn("重复消息，忽略 msgId={}", rowMsgId);
            //ctx.writeAndFlush(MessageFactory.build(MsgType.ACK.getCode(), "已收到"));
            return;
        }
        long msgId = idSegmentService.nextId("message");
        msg.setMsgId(msgId);
        msg.setTimestamp(System.currentTimeMillis());
        ctx.writeAndFlush(MessageFactory.build(MsgType.ACK.getCode(), rowMsgId+":"+msgId));
        userExecutor.submit(()-> shareHandleChat(msg));
    }
    private void shareHandleChat(Message msg){
        long msgId = msg.getMsgId();
        try {
            ChatMessage chatMessage=new ChatMessage().setMsgId(msgId)
                    .setContent(msg.getContent())
                    .setFromUserName(msg.getFromUserName())
                    .setToUserName(msg.getToUserName())
                    .setStatus(0)
                    .setCreateTime(LocalDateTime.now())
                    .setMsgType(msg.getMsgType())
                    .setFileUrl(msg.getFileUrl())
                    .setFileName(msg.getFileName())
                    .setFileSize(msg.getFileSize())
                    .setMimeType(msg.getMimeType());

            String key = singleChatKeyUtil.getChatKey(msg.getFromUserName(),msg.getToUserName());
            chatMessageMapper.insert(chatMessage);
            chatMessage.setCreateTime(null);
            String chatMessageStr = objectMapper.writeValueAsString(chatMessage);
            stringRedisTemplate.opsForZSet().add(key,chatMessageStr,LocalDateTime.now()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli());
            Long size = stringRedisTemplate.opsForZSet().size(key);
            if(size!=null&&size>50){
                stringRedisTemplate.opsForZSet().removeRange(key,0,size-51);
            }
            forwardMessage(msg);
        } catch (Exception e) {
            log.error("处理单聊消息失败 msgId={}",msgId,e);
        }

    }

    public void forwardMessage(Message msg) {
        Channel targetChannel = ONLINE_USERS.get(msg.getToUserName());

        if(targetChannel!=null&&targetChannel.isActive()){
            targetChannel.writeAndFlush(msg);
        }
        //连接失效或者连接不在该实例上
        else {
            String key = USER_CHANNEL_KEY + msg.getToUserName();
            String ip = stringRedisTemplate.opsForValue().get(key);
            //不在该实例上
            if(ip!=null){
                //发送http请求到另一个实例上
                try {
                    String url=ip+"/im/accept";
                    log.info("准备转发到: {}", url);
                    restClient.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(msg)
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception e) {
                    ImOffline offlineMessage=new ImOffline().setCreateTime(LocalDateTime.now())
                            .setMsgId(msg.getMsgId())
                            .setToUserName(msg.getToUserName());
                    offlineMapper.insert(offlineMessage);
                }
            }
            //目标连接失效
            else {
                ImOffline offlineMessage=new ImOffline().setCreateTime(LocalDateTime.now())
                        .setMsgId(msg.getMsgId())
                        .setToUserName(msg.getToUserName());
                offlineMapper.insert(offlineMessage);
            }
        }
    }
    private void handleRecall(ChannelHandlerContext ctx, Message msg) {
        String content = msg.getContent();
        Channel target = ONLINE_USERS.get(msg.getToUserName());
        Channel self = ctx.channel();
        // 丢到业务线程池，不阻塞 IO 线程
        userExecutor.submit(() -> {
            try {
                List<Long> msgIds = objectMapper.readValue(content,
                        new TypeReference<>() {
                        });
                chatMessageService.recallMessage(msgIds,msg.getFromUserName());

                for (Long msgId : msgIds) {
                    Message recallMsg = MessageFactory.build(MsgType.RECALL.getCode(), null);
                    recallMsg.setMsgId(msgId);
                    recallMsg.setFromUserName(msg.getFromUserName());
                    recallMsg.setToUserName(msg.getToUserName());
                    recallMsg.setTimestamp(msg.getTimestamp());

                    if (target != null && target.isActive()) {
                        target.writeAndFlush(recallMsg);
                    }
                    if (self != null && self.isActive() && self != target) {
                        self.writeAndFlush(recallMsg);
                    }
                }
            } catch (Exception e) {
                log.error("处理撤回消息失败", e);
            }
        });
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, Message msg) {
        String username = ctx.channel().attr(USER_NAME).get();
        if(username!=null){
            stringRedisTemplate.opsForValue().set(USER_CHANNEL_KEY+username,"http://127.0.0.1:"+port,60,TimeUnit.SECONDS);
        }
        ctx.writeAndFlush(MessageFactory.build(MsgType.HEARTBEAT_RESP.getCode(), "pong"));
    }
    /**
     * 连接断开时处理
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        String userName = ctx.channel().attr(USER_NAME).get();
        if(userName==null) return;
        //删除指定连接
        ONLINE_USERS.remove(userName,ctx.channel());
        String key = USER_CHANNEL_KEY + userName;
        String currentRoute = stringRedisTemplate.opsForValue().get(key);
        String myRoute = "http://127.0.0.1:" + port;
        if (myRoute.equals(currentRoute)) {
            stringRedisTemplate.delete(key);
        }
        log.info("用户下线 userId={}",userName);
    }

    /**
     * 用户自定义事件或 Idle 事件触发时处理
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.ALL_IDLE) {
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);   // 其他事件继续传播
    }

    /**
     * 处理过程中抛异常时处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
        if (cause instanceof CorruptedFrameException ){
            log.warn("损坏数据包 channelId={}",ctx.channel().id());
        }else {
            log.error("连接异常 channelId={}",ctx.channel().id(),cause);
        }
        ctx.close();
    }

    //服务关闭时调用
    public void clearAllOnlineStatus(){
        for (String username : ONLINE_USERS.keySet()) {
            try {
                stringRedisTemplate.delete(USER_CHANNEL_KEY + username);
            } catch (Exception e) {
                log.error("清理 Redis key 失败 userId={}", username, e);
            }
        }
        ONLINE_USERS.clear();
        log.info("在线状态清理完成");
    }

    @Transactional
    public void pushGroupInviteRequest(List<String> usernameList, Long groupId, String inviteeName) {
        List<ChatMessage> chatMessageList =new ArrayList<>();
        List<ImOffline> imOfflineList=new ArrayList<>();
        for (String username : usernameList) {
            long msgId = idSegmentService.nextId("message");
            chatMessageList.add(new ChatMessage().setGroupId(String.valueOf(groupId))
                    .setFromUserName("im系统")
                    .setMsgType(7)
                    .setContent("inviteeName:" + inviteeName)
                    .setToUserName(username)
                    .setMsgId(msgId)
                    .setStatus(0));
            Channel channel = ONLINE_USERS.get(username);
            if(channel==null||!channel.isActive()){
                imOfflineList.add(new ImOffline().setMsgId(msgId)
                        .setToUserName(username));
            }

        }
        if (!chatMessageList.isEmpty()) {
            chatMessageService.saveBatch(chatMessageList);
            for (ChatMessage cm : chatMessageList) {
                try {
                    String chatKey = singleChatKeyUtil.getChatKey(cm.getFromUserName(),cm.getToUserName());

                    // 为了和 getRecent 返回格式一致，把 createTime 也带上
                    String json = objectMapper.writeValueAsString(cm);

                    long score = cm.getCreateTime() != null
                            ? cm.getCreateTime().atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                            : System.currentTimeMillis();

                    stringRedisTemplate.opsForZSet().add(chatKey, json, score);

                    // 保留最近 50 条
                    Long size = stringRedisTemplate.opsForZSet().size(chatKey);
                    if (size != null && size > 50) {
                        stringRedisTemplate.opsForZSet().removeRange(chatKey, 0, size - 51);
                    }
                } catch (Exception e) {
                    log.error("写 Redis 失败, msgId={}", cm.getMsgId(), e);
                }
            }
        }
        if (!imOfflineList.isEmpty()) {
            offlineMapper.insert(imOfflineList);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String username : usernameList) {
                    Channel channel = ONLINE_USERS.get(username);
                    if (channel == null || !channel.isActive()) {
                        continue;
                    }
                    // ★ 和离线保持一致：type=SINGLE_CHAT, msgType=7, 带 toUserName
                    Message inviteMsg = MessageFactory.build(
                            MsgType.SINGLE_CHAT.getCode(),
                            "inviteeName:" + inviteeName);
                    inviteMsg.setGroupId(String.valueOf(groupId));
                    inviteMsg.setFromUserName("im系统");
                    inviteMsg.setToUserName(username);   // ★ 加这个
                    inviteMsg.setMsgType(7);             // ★ 加这个
                    inviteMsg.setTimestamp(System.currentTimeMillis());
                    channel.writeAndFlush(inviteMsg);
                }
            }
        });
    }
    private void handleGroupChat(ChannelHandlerContext ctx, Message msg) {
        long rowMsgId = msg.getMsgId();

        // 幂等去重
        Boolean ifAbsent = stringRedisTemplate.opsForValue()
                .setIfAbsent("msg:" + rowMsgId, "1", 5, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(ifAbsent)) {
            log.warn("重复群消息，忽略 msgId={}", rowMsgId);
            return;
        }

        // 生成全局 msgId
        long msgId = idSegmentService.nextId("message");
        msg.setMsgId(msgId);
        msg.setTimestamp(System.currentTimeMillis());

        // 立刻回 ACK
        ctx.writeAndFlush(MessageFactory.build(
                MsgType.ACK.getCode(), rowMsgId + ":" + msgId));

        shareHandleGroupChat(msg);
    }

    private void shareHandleGroupChat( Message msg) {
        String groupId = msg.getGroupId();
        List<String> usernameList = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .select(GroupMember::getUsername))
                .stream()
                .map(GroupMember::getUsername)
                .filter(s->!s.equals(msg.getFromUserName()))
                .toList();
        ChatMessage chatMessage=new ChatMessage().setMsgId(msg.getMsgId())
                .setContent(msg.getContent())
                .setFromUserName(msg.getFromUserName())
                .setGroupId(groupId)
                .setStatus(0)
                .setToUserName("groupId:"+groupId)
                .setMsgType(msg.getMsgType())
                .setFileUrl(msg.getFileUrl())
                .setFileName(msg.getFileName())
                .setFileSize(msg.getFileSize())
                .setMimeType(msg.getMimeType());
        chatMessageMapper.insert(chatMessage);
        Integer role = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUsername, msg.getFromUserName())
                        .select(GroupMember::getRole))
                .getFirst().getRole();
        chatMessage.setFromUserRole(role);
        String chatMessageStr;
        try {
            chatMessageStr = objectMapper.writeValueAsString(chatMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        stringRedisTemplate.opsForZSet().add("groupId:"+groupId,chatMessageStr ,System.currentTimeMillis());

        msg.setFromUserRole(role);
        for (String username : usernameList) {
            Channel channel = ONLINE_USERS.get(username);
            if(channel ==null||!channel.isActive()) continue;
            channel.writeAndFlush(msg);
        }
    }
}
