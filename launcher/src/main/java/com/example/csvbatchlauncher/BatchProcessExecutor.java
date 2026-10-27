package com.example.csvbatchlauncher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BatchProcessExecutor {
    private static final Logger log = LoggerFactory.getLogger(BatchProcessExecutor.class);
    private final LauncherProperties properties;
    public BatchProcessExecutor(LauncherProperties properties) { this.properties = properties; }
    public boolean execute(BatchType type, Path file) {
        String jar = properties.batches().get(type.key());
        if (jar == null || jar.isBlank()) throw new IllegalArgumentException("No JAR configured for " + type.key());
        try {
            ProcessBuilder builder = new ProcessBuilder(List.of(javaCommand(), "-jar", jar, "--file=" + file.toAbsolutePath().normalize())).inheritIO();
            builder.environment().putIfAbsent("CSV_ALLOWED_ROOT", properties.watchDirectory().toAbsolutePath().normalize().toString());
            String batchDbUrl = properties.batchDbUrls().getOrDefault(type.key(), properties.batchDbUrl());
            if (batchDbUrl != null && !batchDbUrl.isBlank()) {
                builder.environment().put("DB_URL", batchDbUrl);
            }
            if (properties.batchDbUser() != null && !properties.batchDbUser().isBlank()) {
                builder.environment().put("DB_USER", properties.batchDbUser());
            }
            if (properties.batchDbPass() != null && !properties.batchDbPass().isBlank()) {
                builder.environment().put("DB_PASS", properties.batchDbPass());
            }
            String schema = properties.batchDbSchemas().get(type.key());
            if (schema != null && !schema.isBlank()) builder.environment().put("DB_SCHEMA", schema);
            Process process = builder.start();
            int exit = process.waitFor();
            log.info("event=batch_process_finished type={} file={} exitCode={}", type, file, exit);
            return exit == 0;
        } catch (IOException e) { throw new IllegalStateException("Cannot start batch " + type.key(), e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Batch interrupted", e); }
    }

    private String javaCommand() {
        if (properties.javaCommand() != null && !properties.javaCommand().isBlank()
                && !properties.javaCommand().equalsIgnoreCase("java")) return properties.javaCommand();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (Files.isExecutable(java)) return java.toString();
        Path windowsJava = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (Files.isExecutable(windowsJava)) return windowsJava.toString();
        throw new IllegalStateException("Java 21 executable not found under " + System.getProperty("java.home"));
    }
}
