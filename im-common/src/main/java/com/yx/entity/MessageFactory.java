package com.yx.entity;

/**
 *用于构造系统响应包
 */
public class MessageFactory {
    public static final int magic=0xCAFEBABE;
    public static final int version=1;
    public static Message build(int type, String content){
        Message message=new Message();
        message.setContent(content);
        message.setMagic(magic);
        message.setTimestamp(System.currentTimeMillis());
        message.setType(type);
        message.setVersion(version);
        return message;
    }
    public static Message buildError(String content){
        return build(MsgType.ERROR.getCode(),content);
    }
}
