package com.yxlogin.service;

import com.yxlogin.entity.FollowUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yxlogin.vo.UserVo;

import java.util.List;

/**
 * <p>
 * 用户关注表 服务类
 * </p>
 *
 * @author yu
 * @since 2026-09-23
 */
public interface FollowUserService extends IService<FollowUser> {

    void follow(String toUserName);

    void cancelFollow(String toUserName);

    List<UserVo> getFriends();

    List<UserVo> getMyFollow();

    List<UserVo> getFollowMy();
}
