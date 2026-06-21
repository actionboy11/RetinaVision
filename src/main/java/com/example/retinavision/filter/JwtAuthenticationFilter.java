package com.example.retinavision.filter;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.utils.JwtUtil;
import com.example.retinavision.service.JwtBlacklistService;
import com.example.retinavision.exception.RedisUnavailableException;
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
//SecurityConfig 获取 Filter 引用
// Spring 容器扫描并创建 Bean--> Spring Security 自动检测所有 Filter 类型的 Bean-->自动注册到过滤器链中
//OncePerRequestFilter 是 Spring Security 提供的一个抽象类，用于实现自定义的过滤器逻辑。
// 它确保每个请求只会被过滤器处理一次，避免重复处理同一个请求。
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;   //ObjectMapper 是 Spring Boot 提供的 JSON 处理工具类
    private final JwtBlacklistService jwtBlacklistService;  // 用于检查和管理 JWT 黑名单的服务

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper,
                                   JwtBlacklistService jwtBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.jwtBlacklistService = jwtBlacklistService;
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
            String jti = claims.getId();
            boolean blacklisted = StringUtils.hasText(jti) && jwtBlacklistService.isBlacklisted(jti);
            boolean logoutRequest = "/auth/logout".equals(request.getServletPath());
            // 如果 jti 不存在，或者 JWT 在黑名单中且不是注销请求，则返回 401 未授权错误。
            if (!StringUtils.hasText(jti) || (blacklisted && !logoutRequest)) {
                writeUnauthorized(response);
                return;
            }
            // 从 Token 中提取用户信息
            Integer userId = Integer.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String roleCode = claims.get("roleCode", String.class);
            UserRole role = UserRole.valueOf(roleCode);
            // 创建 CurrentUserVO 对象
            CurrentUserVO currentUser = new CurrentUserVO(userId, username, null, role);
            // 封装成 Authentication 对象
            //Authentication 对象是Spring Security 的核心对象，用于表示用户身份和权限。
            // 保存在ThreadLocal中，用于后续的权限验证和授权。
            //credentials 参数设置为 null，因为我们不需要在后续的安全流程中使用密码进行验证。
            // List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            // 创建一个包含用户角色的权限列表，Spring Security 使用这个列表来进行权限检查。
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            currentUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                    );
            // SecurityContext 是 Spring Security 用于存储当前用户身份和权限信息的上下文对象。
            // SecurityContextHolder.getContext() 方法返回当前线程的 SecurityContext 对象，用于存储用户身份和权限信息。
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 继续处理请求
            filterChain.doFilter(request, response);
            // 如果请求路径是注销路径，则将 JWT 添加到黑名单中，禁止其继续使用。
        } catch (RedisUnavailableException exception) {
            SecurityContextHolder.clearContext();
            writeServiceUnavailable(response);
        } catch (Exception exception) {
            //清空 SecurityContext
            SecurityContextHolder.clearContext();
            //writeUnauthorized(response)的作用是将响应状态码设置为 401（未授权），并返回错误信息给前端。
            writeUnauthorized(response);
        }
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", 50300,
                "message", "认证服务暂时不可用，请稍后重试",
                "data", false
        ));
        response.getWriter().flush();
    }


    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // objectMapper.writeValue() 方法将一个 Java 对象转换为 JSON 格式，并写入到响应的输出流中。
        // 这里我们创建了一个 Map 对象，包含了错误码、错误信息和数据字段，然后将其转换为 JSON 格式返回给前端。
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", 40100,
                "message", "登录已过期，请重新登录",
                "data", false
        ));
        response.getWriter().flush();
    }
}
