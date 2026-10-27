package com.example.csvbatchlauncher;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class NoPrerequisiteReadinessStrategy implements BatchReadinessStrategy {
    @Override
    public boolean supports(BatchType type) {
        return !type.requiresResultsMatch();
    }

    @Override
    public BatchReadiness assess(Path file) {
        return BatchReadiness.available();
    }
}
