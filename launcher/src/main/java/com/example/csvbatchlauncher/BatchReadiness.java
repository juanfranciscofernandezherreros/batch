package com.example.csvbatchlauncher;

public record BatchReadiness(boolean ready, String reason) {
    public static BatchReadiness available() {
        return new BatchReadiness(true, null);
    }

    public static BatchReadiness blocked(String reason) {
        return new BatchReadiness(false, reason);
    }
}
