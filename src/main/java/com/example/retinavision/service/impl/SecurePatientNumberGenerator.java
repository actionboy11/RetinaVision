package com.example.retinavision.service.impl;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecurePatientNumberGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next() {
        return "PT-" + block() + "-" + block();
    }

    private String block() {
        StringBuilder value = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            value.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }
}
