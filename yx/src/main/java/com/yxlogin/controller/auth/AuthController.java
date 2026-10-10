package com.yxlogin.controller.auth;

import com.yxlogin.R.Result;
import com.yxlogin.service.LogoutService;
import com.yxlogin.util.RefreshTokenUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "cookie相关接口")
public class AuthController {
    @Resource
    private RefreshTokenUtil refreshTokenUtil;
    @Resource
    private LogoutService logoutService;
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> data = refreshTokenUtil.refresh(request, response);
        return Result.success(data, "刷新成功");
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<String> logout(HttpServletRequest request, HttpServletResponse response){
        logoutService.logout(request,response);
        return Result.success(null,"退出登录成功");
    }
}
