package com.yxlogin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "账户不能为空")
    public String username;
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\d{3,}$")
    public String password;
}
