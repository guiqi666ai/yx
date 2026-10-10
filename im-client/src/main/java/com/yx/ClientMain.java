package com.yx;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yx.entity.Message;
import com.yx.entity.MessageFactory;
import com.yx.entity.MsgType;

import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args)  {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String userName = args[0];
        String toUserName=args[1];
        String token = args[2];
        ImClient imClient = new ImClient("127.0.0.1", 8889, mapper, userName, token);

        new Thread(() -> {
            try {
                imClient.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        Scanner scanner = new Scanner(System.in);
        System.out.println("==== IM客户端 " + userName + " ====");
        System.out.println("输入消息发送给 u_1002，exit退出");
        while (true) {
            String input = scanner.nextLine();
            if ("exit".equalsIgnoreCase(input)) {
                System.exit(0);
            }
            Message chatMsg = new Message();
            chatMsg.setMagic(MessageFactory.magic);
            chatMsg.setVersion(MessageFactory.version);
            chatMsg.setType(MsgType.SINGLE_CHAT.getCode());
            chatMsg.setFromUserName(userName);
            chatMsg.setToUserName(toUserName);
            chatMsg.setContent(input);
            chatMsg.setTimestamp(System.currentTimeMillis());
            chatMsg.setMsgType(1);
            imClient.send(chatMsg);
        }
    }
}