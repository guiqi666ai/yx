package com.yxlogin.config.Knife4j;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class knife4jConfig {
    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Yx-Login登录系统接口文档")
                                .version("1.0.0")
                                .description("登录认证、OAuth2接口文档")
                );
    }
}
