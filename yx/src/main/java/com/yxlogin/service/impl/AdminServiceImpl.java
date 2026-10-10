package com.yxlogin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yxlogin.entity.User;
import com.yxlogin.mapper.UserMapper;
import com.yxlogin.service.AdminService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AdminServiceImpl implements AdminService {
    @Resource
    private UserMapper userMapper;
    @Override
    public void changeStatus(String username,int status) {
        int row = userMapper.update(new LambdaUpdateWrapper<User>().eq(User::getUsername, username)
                .set(User::getStatus, status));
        if(row==0){
            throw new RuntimeException("用户不存在");
        }
    }
}
