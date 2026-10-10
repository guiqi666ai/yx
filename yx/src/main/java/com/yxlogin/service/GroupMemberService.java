package com.yxlogin.service;

import com.yxlogin.entity.GroupMember;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yxlogin.vo.GroupVo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 群成员表 服务类
 * </p>
 *
 * @author yu
 * @since 2026-10-03
 */
public interface GroupMemberService extends IService<GroupMember> {

    boolean inviteIntoGroup(Long groupId, String username);

    void leaveGroup(Long groupId,String username);

    void stopChat(Long groupId);

    void cancelStopChat(Long groupId);

    void setMemberRole(String toUsername,Long groupId);

    void agreeOrReject(boolean isAgree, Long groupId, String inviteeName);


    List<GroupVo> getMyGroups();

    List<Map<String, Object>> getGroupMembers(Long groupId, int i);
}
