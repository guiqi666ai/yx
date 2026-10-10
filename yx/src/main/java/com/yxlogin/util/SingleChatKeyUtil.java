package com.yxlogin.util;

import com.yxlogin.key.ChatKey;
import org.springframework.stereotype.Component;

@Component
public class SingleChatKeyUtil {
    // 保证A和B，不管谁在前，得到同一个key
    public String getChatKey(String user1, String user2) {
        int compare = user1.compareTo(user2);
        String first, second;
        if (compare < 0) {
            first = user1;
            second = user2;
        } else {
            first = user2;
            second = user1;
        }
        return ChatKey.CHAT_KEY.formatted(first, second);
    }

}
