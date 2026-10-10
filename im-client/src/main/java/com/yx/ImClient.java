package com.yx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.entity.Message;
import com.yx.util.MessageDecoder;
import com.yx.util.MessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class ImClient {
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final String userName;
    private final String token;
    private ClientHandler clientHandler;

    public ImClient(String host, int port, ObjectMapper objectMapper,
                    String userName, String token) {
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
        this.userName = userName;
        this.token = token;
    }

    public void start() throws InterruptedException {
        NioEventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            clientHandler = new ClientHandler(userName, token);   // ← 传进去

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

    public void send(Message msg) {
        clientHandler.sendMsg(msg);
    }
}