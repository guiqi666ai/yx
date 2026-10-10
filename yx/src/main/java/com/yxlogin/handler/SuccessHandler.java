package com.yxlogin.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yxlogin.config.jwt.JwtProperties;
import com.yxlogin.key.JtiKey;
import com.yxlogin.service.UserDetails.LoginUserDetails;
import com.yxlogin.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SuccessHandler implements AuthenticationSuccessHandler {
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private JwtProperties jwtProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        LoginUserDetails registerUser = (LoginUserDetails) authentication.getPrincipal();
        //携带refreshToken,浏览器内置携带，前端看不到
        String refreshToken = jwtUtil.generateRefreshToken(registerUser.getId().toString());
        Cookie cookie = new Cookie("refreshToken",refreshToken);
        cookie.setHttpOnly(true);//放xss攻击
        cookie.setSecure(false);// 只走 HTTPS// 开发环境暂时设为false，todo 改为true
        cookie.setMaxAge(Math.toIntExact(jwtProperties.getRefreshExpire()/ 1000));
        cookie.setPath("/auth");// 只在auth接口携带
        cookie.setAttribute("SameSite","Strict"); // 防 CSRF
        response.addCookie(cookie);

        String token = jwtUtil.generateAccessToken(registerUser.getId().toString(), registerUser.getRoles().getFirst());
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(200);
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("accessToken", token);
        dataMap.put("refreshToken", refreshToken);
        Map<String, Object> map = Map.of("code", 200,
                "data", dataMap);
        String json=objectMapper.writeValueAsString(map);
        response.getWriter().write(json);

        String jitKey= JtiKey.JTI_TEMPLATE +jwtUtil.getJwtId(refreshToken);
        stringRedisTemplate.opsForValue().set(jitKey,registerUser.getId().toString(),jwtProperties.getRefreshExpire(), TimeUnit.MILLISECONDS);
        //立即刷盘
        //response.getWriter().flush();
    }
}
