package com.example.retinavision.analysis.infrastructure.outbox;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "retina.outbox")
public class AnalysisOutboxProperties {

    @NotNull
    private Duration fixedDelay = Duration.ofSeconds(1);

    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @NotNull
    private Duration retryDelay = Duration.ofSeconds(10);

    @NotNull
    private Duration claimTimeout = Duration.ofMinutes(2);

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        this.fixedDelay = fixedDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Duration getClaimTimeout() {
        return claimTimeout;
    }

    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = claimTimeout;
    }

    @AssertTrue(message = "outbox durations must be positive")
    public boolean isDurationsPositive() {
        return isPositive(fixedDelay)
                && isPositive(retryDelay)
                && isPositive(claimTimeout);
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
