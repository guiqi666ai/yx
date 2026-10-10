package com.yx.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yx.entity.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class MessageDecoder extends ByteToMessageDecoder {
    private final ObjectMapper objectMapper;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        //连长度头都读不倒->半包,直接返回，等待后续数据
        if(in.readableBytes()<4){
            return;
        }
        in.markReaderIndex();//打标记，读之前的指针位置
        int length = in.readInt();//这个readInt只读4个字节，返回要读取的长度，指针移动4个字节
        if(in.readableBytes()<length){
            in.resetReaderIndex();
            return;
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        try {
            Message message = objectMapper.readValue(bytes, Message.class);
            out.add(message);
        } catch (IOException e) {

            throw new RuntimeException("json解析失败",e);
        }
    }
}
