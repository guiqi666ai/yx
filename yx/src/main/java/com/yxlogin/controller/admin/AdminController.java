package com.yxlogin.controller.admin;

import com.yxlogin.R.Result;
import com.yxlogin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "管理员操作接口")
public class AdminController {
    @Resource
    private AdminService adminService;
    @Operation(summary = "修改用户账号状态")
    @PostMapping("/changeStatus")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> changeStatus(@RequestParam String username, int status){
        adminService.changeStatus(username,status);
        return Result.success(null,"修改成功");
    }
}
