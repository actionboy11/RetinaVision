package com.example.retinavision.agent;

public record PatientReportExplanationResult(boolean available, String explanation, String message) {
    public static PatientReportExplanationResult available(String explanation) {
        return new PatientReportExplanationResult(true, explanation, null);
    }

    public static PatientReportExplanationResult unavailable(String message) {
        return new PatientReportExplanationResult(false, null, message);
    }
}
