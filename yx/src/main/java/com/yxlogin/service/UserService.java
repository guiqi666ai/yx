package com.yxlogin.service;

import com.yxlogin.dto.UpdateUserDTO;
import com.yxlogin.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yxlogin.vo.UserVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * <p>
 * 登录用户表 服务类
 * </p>
 *
 * @author yu
 * @since 2026-09-12
 */
public interface UserService extends IService<User> {

    void registerUser(@NotBlank(message = "账户不能为空") String username, @NotBlank(message = "密码不能为空") String password);

    void registerAdmin(@NotBlank(message = "账户不能为空") String username, @NotBlank(message = "密码不能为空") String password);

    void revoke(HttpServletRequest request);

    void updatePersonalInfo(UpdateUserDTO updateUserDTO);

    UserVo searchUser(String userName);

    List<String> getUnreadSenders();
}
