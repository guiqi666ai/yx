package com.yx.util;

import com.yx.entity.Message;
import com.yx.entity.MessageFactory;
import com.yx.entity.MsgType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private final String userName;
    private final String token;
    private Channel channel;

    /** 支持多个监听器，每个聊天窗口注册一个 */
    private final List<Consumer<Message>> listeners = new CopyOnWriteArrayList<>();

    /** ★ 没有监听器时缓存消息，等监听器注册后补发 */
    private final List<Message> pendingMessages = new CopyOnWriteArrayList<>();

    /** ★ 缓存消息上限，防止内存爆掉 */
    private static final int MAX_PENDING = 500;

    public ClientHandler(String userName, String token) {
        this.userName = userName;
        this.token = token;
    }

    /**
     * 添加监听器。
     * 如果之前有缓存的消息，会立刻补发给这个监听器。
     */
    public void addMessageListener(Consumer<Message> listener) {
        if (listener == null) return;
        listeners.add(listener);

        // ★ 补发缓存的消息
        if (!pendingMessages.isEmpty()) {
            log.info("补发缓存消息，数量={}", pendingMessages.size());
            List<Message> snapshot = List.copyOf(pendingMessages);
            pendingMessages.clear();
            for (Message msg : snapshot) {
                try {
                    listener.accept(msg);
                } catch (Exception e) {
                    log.error("补发消息失败", e);
                }
            }
        }
    }

    /** 移除监听器 */
    public void removeMessageListener(Consumer<Message> listener) {
        listeners.remove(listener);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        log.info("✅客户端 {} 连接成功", userName);

        Message login = new Message();
        login.setMagic(MessageFactory.magic);
        login.setVersion(MessageFactory.version);
        login.setType(MsgType.LOGIN.getCode());
        login.setFromUserName(userName);
        login.setToken(token);
        login.setTimestamp(System.currentTimeMillis());
        ctx.writeAndFlush(login);

        ctx.executor().scheduleAtFixedRate(() -> {
            if (channel != null && channel.isActive()) {
                Message heartbeat = MessageFactory.build(MsgType.HEARTBEAT.getCode(), "ping");
                heartbeat.setFromUserName(userName);
                channel.writeAndFlush(heartbeat);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        MsgType type = MsgType.fromCode(msg.getType());

        boolean shouldDispatch =
                type == MsgType.SINGLE_CHAT
                        || type == MsgType.RECALL
                        || type == MsgType.RECALL_FAIL
                        || type == MsgType.ACK
                        || type == MsgType.GROUP_INVITE_REQUEST
                ||type == MsgType.GROUP_CHAT;

        if (!shouldDispatch) {
            return;
        }

        // ★ 没有监听器 → 缓存消息，等有监听器时补发
        if (listeners.isEmpty()) {
            if (pendingMessages.size() < MAX_PENDING) {
                pendingMessages.add(msg);
                log.info("无监听器，缓存消息 type={}, msgType={}, from={}",
                        msg.getType(), msg.getMsgType(), msg.getFromUserName());
            } else {
                log.warn("缓存已满，丢弃消息 type={}", msg.getType());
            }
            return;
        }

        // 有监听器 → 正常分发
        for (Consumer<Message> listener : listeners) {
            try {
                listener.accept(msg);
            } catch (Exception e) {
                log.error("分发消息失败", e);
            }
        }
    }

    public void sendMsg(Message message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        } else {
            log.error("❌通道未连接，发送失败");
        }
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("🔌与服务端连接断开");
        this.channel = null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }
}