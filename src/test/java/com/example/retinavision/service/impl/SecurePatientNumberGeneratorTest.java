package com.example.retinavision.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurePatientNumberGeneratorTest {

    @Test
    void generatesHumanReadableOpaquePatientNumbersWithoutCollisions() {
        SecurePatientNumberGenerator generator = new SecurePatientNumberGenerator();
        Set<String> values = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String value = generator.next();
            assertThat(value).matches("PT-[A-Z0-9]{4}-[A-Z0-9]{4}");
            values.add(value);
        }

        assertThat(values).hasSize(1000);
    }
}
