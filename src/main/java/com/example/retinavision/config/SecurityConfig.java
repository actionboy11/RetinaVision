package com.example.retinavision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration    //表明这是一个配置类，Spring 会扫描并加载这个类中的 Bean 定义。
@EnableWebSecurity  //启用 Spring Security 的 web 安全功能，允许我们自定义安全配置。
public class SecurityConfig {

    // 定义一个 Bean，用于密码加密。这里使用 BCryptPasswordEncoder，它是一种强哈希算法，适合存储密码。
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 定义一个 Bean，用于配置安全过滤链。这个方法接受一个 HttpSecurity 对象作为参数，用于配置 HTTP 安全设置。
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Codex修复：前后端分离接口不使用表单 CSRF Token，否则 POST 注册请求可能被拦截为 403。
            .csrf(AbstractHttpConfigurer::disable)
            // Codex修复：启用下面的 corsConfigurationSource，支持前端开发服务器访问后端。
            .cors(cors -> {})
            // Codex修复：关闭默认表单登录和 Basic 登录，避免未登录接口被默认安全流程接管。
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Codex修复：放行浏览器预检请求，避免开发期跨域/代理预检返回 403。
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Codex修复：放行 Spring Boot 错误转发路径，避免真实后端异常被安全层二次包装成 403。
                .requestMatchers("/error").permitAll()
                // Codex修复：注册和登录属于匿名接口；项目配置了 context-path=/api，这里匹配 servlet 内路径 /auth/**。
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Codex修复：允许本地 Vite 开发服务器跨域调用 Spring Boot。
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        // Codex修复：允许前端发送 Content-Type 和 Authorization 等请求头。
        configuration.setAllowedHeaders(List.of("*"));
        // Codex修复：允许注册、登录以及后续业务接口需要的 HTTP 方法。
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

