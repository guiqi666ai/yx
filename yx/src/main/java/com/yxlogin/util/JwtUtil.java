package com.yxlogin.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import com.yxlogin.config.jwt.JwtProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    @Resource
    private JwtProperties jwtProperties;
    //生成accessToken
    public String generateAccessToken(String userId,String role){
        return JWT.create()
                .setPayload("userId",userId)
                .setPayload("type","access")
                .setPayload("role",role)
                .setIssuedAt(new Date())
                .setExpiresAt(new Date(
                        System.currentTimeMillis()+jwtProperties.getAccessExpire()
                ))
                .setKey(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8))
                .sign();
    }
    //生成refreshToken
    public String generateRefreshToken(String userId){
        String jwtId = UUID.randomUUID().toString();
        return JWT.create()
                .setPayload("userId",userId)
                .setPayload("type","refresh")
                .setJWTId(jwtId)
                .setIssuedAt(new Date())
                .setExpiresAt(new Date(
                        System.currentTimeMillis()+jwtProperties.getRefreshExpire()
                ))
                .setKey(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8))
                .sign();
    }
    public boolean isVerifySignature(String token){
        try {
            return JWT.of(token).setKey(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)).verify();
        } catch (Exception e) {
            return false;
        }
    }
    public boolean isExpired(String token){
        try {
            JWTValidator.of(token).validateDate(new Date());
            return false;
        } catch (ValidateException e) {
            return true;
        }
    }
    public boolean isVerify(String token){
        return isVerifySignature(token)&&!isExpired(token);
    }
    public String getUserId(String token){
        return (String) JWTUtil.parseToken(token).getPayload("userId");
    }
    public String getType(String token){
        return JWTUtil.parseToken(token).getPayload("type").toString();
    }
    public String getJwtId(String refreshToken){
        return JWTUtil.parseToken(refreshToken).getPayload("jti").toString();
    }
    public String getRole(String token){
        return JWTUtil.parseToken(token).getPayload("role").toString();
    }
}
