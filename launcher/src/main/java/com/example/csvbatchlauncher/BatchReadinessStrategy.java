package com.example.csvbatchlauncher;

import java.nio.file.Path;

public interface BatchReadinessStrategy {
    boolean supports(BatchType type);

    BatchReadiness assess(Path file);
}
