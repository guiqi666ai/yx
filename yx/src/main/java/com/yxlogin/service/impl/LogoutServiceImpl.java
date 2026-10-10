package com.yxlogin.service.impl;

import com.yxlogin.key.JtiKey;
import com.yxlogin.service.LogoutService;
import com.yxlogin.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LogoutServiceImpl implements LogoutService {
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String refreshToken=null;
        if(cookies==null||cookies.length==0){
            return;
        }
        for (Cookie cookie : cookies) {
            if(cookie.getName().equals("refreshToken")){
                refreshToken=cookie.getValue();
                break;
            }
        }
        if(refreshToken==null){
            return;
        }
        String jwtId = jwtUtil.getJwtId(refreshToken);
        String key= JtiKey.JTI_TEMPLATE+jwtId;
        redisTemplate.delete(key);

        Cookie cookie = new Cookie("refreshToken","");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);//todo 改成false
        cookie.setPath("/auth");
        response.addCookie(cookie);
    }
}
