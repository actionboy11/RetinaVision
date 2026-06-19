package com.example.retinavision.config;

import com.example.retinavision.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;


@Configuration    //表明这是一个配置类，Spring 会扫描并加载这个类中的 Bean 定义。
@EnableWebSecurity  //启用 Spring Security 的 web 安全功能，允许我们自定义安全配置。
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    // 定义一个 Bean，用于密码加密。这里使用 BCryptPasswordEncoder，它是一种强哈希算法，适合存储密码。
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 定义一个 Bean，用于配置安全过滤链。这个方法接受一个 HttpSecurity 对象作为参数，用于配置 HTTP 安全设置。
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 前后端分离接口不使用表单 CSRF Token，否则 POST 注册请求可能被拦截为 403。
            .csrf(AbstractHttpConfigurer::disable)
            // 启用下面的 corsConfigurationSource，支持前端开发服务器访问后端。
                //从 ApplicationContext 中查找 CorsConfigurationSource 类型的 Bean-->使用这个 Bean 创建 CorsFilter-->将 CorsFilter 添加到过滤器链
            .cors(cors -> {})
            // 关闭默认表单登录和 Basic 登录，避免未登录接口被默认安全流程接管。
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
                // 关闭 Spring Security 创建的会话，使用 JWT 登录。
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置异常处理，返回 401 错误信息。
            .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), Map.of(
                        "code", 40100,
                        "message", "登录已过期，请重新登录",
                        "data", false
                ));
                response.getWriter().flush();  // ← 保数据被写入响应
            }))
            .authorizeHttpRequests(auth -> auth
                // 放行浏览器预检请求，避免开发期跨域/代理预检返回 403。
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 放行 Spring Boot 错误转发路径，避免真实后端异常被安全层二次包装成 403。
                .requestMatchers("/error").permitAll()
                // 注册和登录属于匿名接口；项目配置了 context-path=/api，这里匹配 servlet 内路径 /auth/**。
                    //匿名接口的作用：用户未登录时，允许访问注册和登录接口，其他接口需要登录后才能访问。
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                // 有限重试与死信恢复功能完善：死信恢复会重新执行失败任务，只允许管理员操作。
                .requestMatchers("/admin/dead-letters/**").hasRole("ADMIN")
                .anyRequest().authenticated()  // 其他接口需要登录后才能访问
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);   //// ↑ 显式添加到过滤器链，并指定位置
        return http.build();  //创建 SecurityFilterChain   Spring Security 管理整个过滤器链
    }

    // 创建 CORS 配置源，用于配置跨域请求。
    //CorsConfigurationSource
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 【启动时执行】创建 CORS 配置对象
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许本地 Vite 开发服务器跨域调用 Spring Boot。
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        //允许前端发送 Content-Type 和 Authorization 等请求头。
        configuration.setAllowedHeaders(List.of("*"));
        // 允许注册、登录以及后续业务接口需要的 HTTP 方法。
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
