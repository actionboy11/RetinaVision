package com.example.retinavision.analysis.infrastructure.outbox;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisOutboxPropertiesTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsConfiguredBatchBoundariesAndPositiveDurations() {
        AnalysisOutboxProperties properties = properties();

        properties.setBatchSize(1);
        assertThat(validator.validate(properties)).isEmpty();

        properties.setBatchSize(500);
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsBatchSizeOutsideOneToFiveHundred() {
        AnalysisOutboxProperties properties = properties();

        properties.setBatchSize(0);
        assertThat(messages(validator.validate(properties)))
                .anyMatch(message -> message.contains("1"));

        properties.setBatchSize(501);
        assertThat(messages(validator.validate(properties)))
                .anyMatch(message -> message.contains("500"));
    }

    @Test
    void rejectsNullZeroOrNegativeDurations() {
        assertInvalidDuration(null, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertInvalidDuration(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertInvalidDuration(Duration.ofSeconds(1), Duration.ofSeconds(-1), Duration.ofSeconds(1));
        assertInvalidDuration(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO);
    }

    private void assertInvalidDuration(
            Duration fixedDelay,
            Duration retryDelay,
            Duration claimTimeout) {
        AnalysisOutboxProperties properties = properties();
        properties.setFixedDelay(fixedDelay);
        properties.setRetryDelay(retryDelay);
        properties.setClaimTimeout(claimTimeout);

        assertThat(validator.validate(properties)).isNotEmpty();
    }

    private Set<String> messages(
            Set<ConstraintViolation<AnalysisOutboxProperties>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    private AnalysisOutboxProperties properties() {
        AnalysisOutboxProperties properties = new AnalysisOutboxProperties();
        properties.setFixedDelay(Duration.ofSeconds(1));
        properties.setBatchSize(50);
        properties.setRetryDelay(Duration.ofSeconds(10));
        properties.setClaimTimeout(Duration.ofMinutes(2));
        return properties;
    }
}
