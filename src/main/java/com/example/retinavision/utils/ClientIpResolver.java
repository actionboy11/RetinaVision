package com.example.retinavision.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


//  Utility class to resolve the client's IP address from the HttpServletRequest.
//  用于从 HttpServletRequest 中提取客户端 IP 地址
@Component
public class ClientIpResolver {
    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
    }
}
