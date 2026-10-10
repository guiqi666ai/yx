package com.yx;

import com.yx.entity.Message;
import com.yx.entity.MessageFactory;
import com.yx.entity.MsgType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientHandler extends SimpleChannelInboundHandler<Message> {

    private final String userName;
    private final String token;
    private Channel channel;

    public ClientHandler(String userName, String token) {
        this.userName = userName;
        this.token = token;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        log.info("✅客户端 {} 连接成功", userName);

        Message login = new Message();
        login.setMagic(MessageFactory.magic);       // ← 补
        login.setVersion(MessageFactory.version);   // ← 补
        login.setType(MsgType.LOGIN.getCode());
        login.setFromUserName(userName);
        login.setToken(token);
        login.setTimestamp(System.currentTimeMillis());
        channel.writeAndFlush(login);
    }

    // 收到服务端推送消息
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        log.info("📥收到服务端消息：type={},from={},content={}",
                msg.getType(), msg.getFromUserName(), msg.getContent());
    }

    // 对外发送消息
    public void sendMsg(Message message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        } else {
            log.error("❌通道未连接，发送失败");
        }
    }

    // 获取channel，外部可判断是否在线
    public Channel getChannel() {
        return channel;
    }

    // 连接断开
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("🔌与服务端连接断开");
        this.channel = null;
    }

    // 异常捕获
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("客户端异常", cause);
        ctx.close();
    }
}
