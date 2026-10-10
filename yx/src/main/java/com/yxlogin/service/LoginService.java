package com.yxlogin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yxlogin.entity.User;
import com.yxlogin.mapper.UserMapper;
import com.yxlogin.service.UserDetails.LoginUserDetails;
import jakarta.annotation.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoginService implements UserDetailsService {
    @Resource
    private UserMapper userMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper
                .selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if(user ==null){
            throw new RuntimeException("用户不存在");
        }
        if(user.getStatus()==0){
            throw new RuntimeException("该用户已被禁用");
        }
        String password = user.getPassword();
        return LoginUserDetails.builder()
                .id(user.getId())
                .roles(List.of(user.getRole()))
                .username(username)
                .password(password)
                .build();
    }
}
