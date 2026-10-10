package com.yxlogin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.util.MessageDecoder;
import com.yx.util.MessageEncoder;
import com.yxlogin.handler.ImServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ImService {
    @Value("${im.port:8888}")
    private int port;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ImServerHandler handler;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    @PostConstruct
    public void start()throws InterruptedException{
        boss=new NioEventLoopGroup(1);
        worker=new NioEventLoopGroup();
        ServerBootstrap b=new ServerBootstrap();
        b.group(boss,worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE,true)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch)  {
                        ChannelPipeline p= ch.pipeline();//连接的处理链
                        p.addLast(new MessageEncoder(objectMapper));
                        p.addLast(new MessageDecoder(objectMapper));
                        p.addLast(new IdleStateHandler(0,0,60, TimeUnit.SECONDS));
                        p.addLast(handler);
                    }
                });
        b.bind(port).sync();
        log.info("IM服务启动，端口：{}",port);
    }
    @PreDestroy
    public void stop(){
        log.info("IM 服务准备关闭");
        try {
            handler.clearAllOnlineStatus();
        } catch (Exception e) {
            log.error("清理在线状态失败", e);
        }
        //shutdownGracefully()不会立刻杀掉线程，
        //而是先停止接收新任务，等正在处理的任务完成后，再关闭线程。
        if(boss!=null) boss.shutdownGracefully();
        if(worker!=null) worker.shutdownGracefully();
    }
}
