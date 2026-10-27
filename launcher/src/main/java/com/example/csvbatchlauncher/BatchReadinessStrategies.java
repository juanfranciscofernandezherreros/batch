package com.example.csvbatchlauncher;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BatchReadinessStrategies {
    private final List<BatchReadinessStrategy> strategies;

    public BatchReadinessStrategies(List<BatchReadinessStrategy> strategies) {
        this.strategies = strategies;
    }

    public BatchReadiness assess(BatchType type, Path file) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No readiness strategy configured for " + type))
                .assess(file);
    }
}
