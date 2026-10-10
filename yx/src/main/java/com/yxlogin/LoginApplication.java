package com.yxlogin;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@SpringBootApplication
@MapperScan("com.yxlogin.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class LoginApplication {
    public static void main(String[] args)  {
        log.info("登录系统启动");
        SpringApplication.run(LoginApplication.class, args);
    }
}