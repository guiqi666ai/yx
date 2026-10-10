package com.yxlogin.dto;

import lombok.Data;

@Data
public class UpdateUserDTO {
    private String username;
    private String password;
    private String nickname;
}
