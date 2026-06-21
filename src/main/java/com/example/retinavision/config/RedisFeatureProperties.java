package com.example.retinavision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
// 配置类，用于加载 Redis 相关的配置属性。
@ConfigurationProperties(prefix = "retina.redis")
public class RedisFeatureProperties {

    private String keyPrefix = "rv";
    private final Login login = new Login();
    private final Ai ai = new Ai();

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Login getLogin() {
        return login;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Login {
        private Duration window = Duration.ofMinutes(15);
        private int usernameMaxFailures = 5;
        private int ipMaxFailures = 20;

        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public int getUsernameMaxFailures() { return usernameMaxFailures; }
        public void setUsernameMaxFailures(int value) { this.usernameMaxFailures = value; }
        public int getIpMaxFailures() { return ipMaxFailures; }
        public void setIpMaxFailures(int value) { this.ipMaxFailures = value; }
    }

    public static class Ai {
        private int minuteLimit = 10;
        private int dailyLimit = 500;
        private String zoneId = "Asia/Shanghai";

        public int getMinuteLimit() { return minuteLimit; }
        public void setMinuteLimit(int minuteLimit) { this.minuteLimit = minuteLimit; }
        public int getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
        public String getZoneId() { return zoneId; }
        public void setZoneId(String zoneId) { this.zoneId = zoneId; }
    }
}
