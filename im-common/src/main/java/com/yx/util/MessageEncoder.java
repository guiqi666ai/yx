package com.yx.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.entity.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class MessageEncoder extends MessageToByteEncoder<Message> {
    private final ObjectMapper objectMapper;
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(msg);
            out.writeInt(bytes.length);
            out.writeBytes(bytes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("编码失败",e);
        }
    }
}
