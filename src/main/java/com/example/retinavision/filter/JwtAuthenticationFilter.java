package com.example.retinavision.filter;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
//SecurityConfig 获取 Filter 引用 ，
// Spring 容器扫描并创建 Bean--> Spring Security 自动检测所有 Filter 类型的 Bean-->自动注册到过滤器链中
//OncePerRequestFilter 的作用是确保每个请求只执行一次过滤，而不是控制是否加入过滤器链。
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;   //ObjectMapper 是 Spring Boot 提供的 JSON 处理工具类

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    //前端请求 → HTTP Header (携带 JWT) → Spring Security 过滤器 → Authentication 对象
    // 登录成功 → Authentication 存入 SecurityContext（ThreadLocal）
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain   // 过滤器链
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");  // ← 从请求头读取

        if (!StringUtils.hasText(authorization)) {   //hasText() 判断字符串是否为空
            filterChain.doFilter(request, response);    //doFilter() 方法 1继续执行过滤器链中的下一个过滤器
            return;
        }

        if (!authorization.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        try {
            String token = authorization.substring(7); // ← 去掉 "Bearer " 前缀
            Claims claims = jwtUtil.parseToken(token);    // ← 解析 Token
            // 从 Token 中提取用户信息
            Integer userId = Integer.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String roleCode = claims.get("roleCode", String.class);
            UserRole role = UserRole.valueOf(roleCode);
            // 创建 CurrentUserVO 对象
            CurrentUserVO currentUser = new CurrentUserVO(userId, username, null, role);
            // 封装成 Authentication 对象
            //Authentication 对象是Spring Security 的核心对象，用于表示用户身份和权限。保存在ThreadLocal中，用于后续的权限验证和授权。
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            currentUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                    );
            // 存入 SecurityContext（ThreadLocal）
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 继续处理请求
            filterChain.doFilter(request, response);
        } catch (Exception exception) {
            //清空 SecurityContext
            SecurityContextHolder.clearContext();
            //writeUnauthorized(response)的作用是将响应状态码设置为 401（未授权），并返回错误信息给前端。
            writeUnauthorized(response);
        }
    }


    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", 40100,
                "message", "登录已过期，请重新登录",
                "data", false
        ));
        response.getWriter().flush();
    }
}
