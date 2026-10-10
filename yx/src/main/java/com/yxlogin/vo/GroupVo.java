package com.yxlogin.vo;

import lombok.Data;

@Data
public class GroupVo {
    private Long groupId;
    private String groupName;
    private Integer memberCount;   // 成员数
    private Integer myRole;        // 我的角色：1群主 2管理员 3普通成员
}