package com.yxlogin.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

@Component
public class FailureHandler implements AuthenticationFailureHandler {
    @Resource
    private ObjectMapper objectMapper;
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String message;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 401
        response.setContentType("application/json;charset=UTF-8");
        if (exception instanceof BadCredentialsException) {
            message = "用户名或密码错误";
        } else if (exception instanceof DisabledException) {
            message = "账号已被禁用";
        } else if (exception instanceof LockedException) {
            message = "账号已被锁定";
        } else if (exception instanceof AccountExpiredException) {
            message = "账号已过期";
        } else if (exception instanceof CredentialsExpiredException) {
            message = "密码已过期";
        } else {
            message = "登录失败";
        }
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("code", 401,
                "message", message)));
    }
}
