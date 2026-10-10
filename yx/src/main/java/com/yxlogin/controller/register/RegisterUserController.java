package com.yxlogin.controller.register;

import com.yxlogin.R.Result;
import com.yxlogin.dto.RegisterDTO;
import com.yxlogin.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 注册 前端控制器
 * </p>
 *
 * @author yu
 * @since 2026-09-12
 */
@RestController
@RequestMapping("/register")
@Tag(name="注册接口")
@Slf4j
public class RegisterUserController {
    @Resource
    private UserService loginUserService;

    @PostMapping("/user")
    @Operation(summary = "用户注册")
    public Result<Object> registerUser(@Valid @RequestBody RegisterDTO registerDTO){
        loginUserService.registerUser(registerDTO.username, registerDTO.password);
        return Result.success();
    }

    @PostMapping("/admin")
    @Operation(summary = "管理员注册")
    public Result<Object> registerAdmin(@Valid @RequestBody RegisterDTO registerDTO){
        loginUserService.registerAdmin(registerDTO.username, registerDTO.password);
        return Result.success();
    }
}
