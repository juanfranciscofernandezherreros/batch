package com.example.csvbatchlauncher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.launcher")
public record LauncherProperties(Path watchDirectory, Duration pollInterval, int stabilityChecks, Path stateFile,
        String javaCommand, String batchDbUrl, String batchDbUser, String batchDbPass,
        Map<String, String> batchDbUrls, Map<String, String> batchDbSchemas, Map<String, String> batches) {
    public LauncherProperties {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) throw new IllegalArgumentException("poll-interval must be positive");
        if (stabilityChecks < 1) throw new IllegalArgumentException("stability-checks must be positive");
    }
}
