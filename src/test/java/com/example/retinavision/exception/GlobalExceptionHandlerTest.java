package com.example.retinavision.exception;

import com.example.retinavision.constant.ErrorMessageSignal;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitUsesHttp429AndRetryAfterHeader() {
        ResponseEntity<?> response = handler.handleRateLimitException(
                new RateLimitException("请求过于频繁", 42)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("code", ErrorMessageSignal.TOO_MANY_REQUESTS);
    }

    @Test
    void redisUnavailableUsesHttp503() {
        ResponseEntity<?> response = handler.handleRedisUnavailableException(
                new RedisUnavailableException("Redis unavailable")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).hasFieldOrPropertyWithValue("code", ErrorMessageSignal.SERVICE_UNAVAILABLE);
    }

    @Test
    void objectAuthorizationErrorsUseRealHttpStatus() {
        ResponseEntity<?> forbidden = handler.handleBaseException(
                new BaseException(ErrorMessageSignal.FORBIDDEN, "权限不足"));
        ResponseEntity<?> notFound = handler.handleBaseException(
                new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在"));

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
    }
}
