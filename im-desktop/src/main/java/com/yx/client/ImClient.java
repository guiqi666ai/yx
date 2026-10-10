package com.yx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.entity.Message;
import com.yx.util.ClientHandler;
import com.yx.util.MessageDecoder;
import com.yx.util.MessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.function.Consumer;

public class ImClient {
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final ClientHandler clientHandler;

    public ImClient(String host, int port, ObjectMapper objectMapper,
                    String userName, String token) {
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
        this.clientHandler = new ClientHandler(userName, token);
    }

    public void start() throws InterruptedException {
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new MessageDecoder(objectMapper));
                            pipeline.addLast(new MessageEncoder(objectMapper));
                            pipeline.addLast(clientHandler);
                        }
                    });

            ChannelFuture connectFuture = bootstrap.connect(host, port).sync();
            connectFuture.channel().closeFuture().sync();
        } finally {
            workGroup.shutdownGracefully();
        }
    }

    /** 添加监听器（每个聊天窗口注册一个） */
    public void addMessageListener(Consumer<Message> listener) {
        clientHandler.addMessageListener(listener);
    }

    /** 移除监听器（窗口关闭时调用） */
    public void removeMessageListener(Consumer<Message> listener) {
        clientHandler.removeMessageListener(listener);
    }

    public void send(Message msg) {
        clientHandler.sendMsg(msg);
    }
}