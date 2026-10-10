package com.yxlogin.vo;

import lombok.Data;

@Data
public class UserVo {
    private String username;
    private String nickname;
    private Boolean isFollowed;
}
