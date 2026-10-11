package com.yxlogin.filter;

import com.yxlogin.util.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // 没有 Authorization 头 → 未登录
        if (header == null || !header.startsWith("Bearer ")) {
            writeJson(response, "未登录，请先登录", 401);
            return;
        }

        String token = header.substring(7);
        if (token.isBlank()) {
            writeJson(response, "token 不能为空", 401);
            return;
        }

        // token 校验失败 → 客户端触发刷新
        if (!jwtUtil.isVerify(token)) {
            writeJson(response, "token 已过期，请刷新", 401);
            return;
        }

        // 解析用户信息，设置 SecurityContext
        String userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * 统一返回 HTTP 200 + body {code:401}，
     * 让客户端按 body 里的 code 判断，而不是 HTTP 状态码。
     */
    private void writeJson(HttpServletResponse response, String msg, int code) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);   // ★ 关键：永远 200
        String json = "{\"code\":" + code + ",\"message\":\"" + msg + "\"}";
        response.getWriter().write(json);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")
                || path.startsWith("/doc.html")
                || path.startsWith("/favicon.ico")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/login")
                ||path.startsWith("/register")
                || path.startsWith("/.well-known/appspecific/com.chrome.devtools.json");
    }
}