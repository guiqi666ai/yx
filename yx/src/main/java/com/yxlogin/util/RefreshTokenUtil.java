package com.yxlogin.util;

import com.yxlogin.config.jwt.JwtProperties;
import com.yxlogin.entity.User;
import com.yxlogin.key.JtiKey;
import com.yxlogin.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenUtil {

    @Resource private JwtUtil jwtUtil;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private UserMapper userMapper;
    @Resource private JwtProperties jwtProperties;

    /**
     * 刷新 Access Token。
     * 优先从 Header 读 Refresh Token（JavaFX 客户端），
     * 读不到再回退到 Cookie（浏览器）。
     */
    public Map<String, String> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) throw new RuntimeException("未携带刷新令牌");
        if (!jwtUtil.isVerify(refreshToken)) throw new RuntimeException("刷新令牌无效");

        String userId = jwtUtil.getUserId(refreshToken);
        String jwtId = jwtUtil.getJwtId(refreshToken);
        String key = JtiKey.JTI_TEMPLATE + jwtId;

        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new RuntimeException("刷新令牌已失效，请重新登录");
        }
        stringRedisTemplate.delete(key);

        // 生成新 Refresh Token
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);
        String newJti = jwtUtil.getJwtId(newRefreshToken);
        stringRedisTemplate.opsForValue().set(
                JtiKey.JTI_TEMPLATE + newJti,
                userId,
                jwtProperties.getRefreshExpire(),
                TimeUnit.MILLISECONDS);

        // 下发 Cookie（浏览器用）
        Cookie cookie = new Cookie("refreshToken", newRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(Math.toIntExact(jwtProperties.getRefreshExpire() / 1000));
        cookie.setPath("/auth");
        response.addCookie(cookie);

        // 新 Access Token
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        String newAccessToken = jwtUtil.generateAccessToken(userId, user.getRole());

        // ★ 两个都返回
        Map<String, String> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", newRefreshToken);
        return result;
    }

    /**
     * 从请求里提取 Refresh Token：
     * - Header: Authorization: Bearer xxx   （JavaFX 客户端走这条）
     * - Cookie: refreshToken=xxx            （浏览器走这条）
     */
    private String extractRefreshToken(HttpServletRequest request) {
        // 优先 Header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 回退 Cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("refreshToken".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}