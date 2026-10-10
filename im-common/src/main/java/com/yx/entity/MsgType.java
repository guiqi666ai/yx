package com.yx.entity;

import java.util.HashMap;
import java.util.Map;
public enum MsgType {
    LOGIN(1),
    LOGIN_RESP(2),
    SINGLE_CHAT(3),
    GROUP_CHAT(4),
    HEARTBEAT(5),
    HEARTBEAT_RESP(6),
    ACK(7),
    ERROR(8),
    RECALL(9),
    RECALL_FAIL(10),
    GROUP_INVITE_REQUEST(11);
    private final int code;
    MsgType(int code){
        this.code=code;
    }
    public int getCode(){
        return code;
    }
    public static final Map<Integer,MsgType> map=new HashMap<>();
    static {
        for (MsgType t:values()) {
            map.put(t.code,t);
        }
    }
    public static MsgType fromCode(int code){
        return map.get(code);
    }
}
