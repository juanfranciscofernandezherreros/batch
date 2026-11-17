package com.example.csvbatchlauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    /** Resultado de un batch: si terminó bien y, si no, el motivo sacado de su salida. */
    public record Resultado(boolean ok, String motivo) {}

    public boolean execute(BatchType type, Path file) { return ejecutar(type, file).ok(); }

    /**
     * Lanza el batch con su salida en {@code <BATCH_LOG_DIR>/<tipo>_<csv>.log} (por defecto
     * {@code batch-logs} junto a la carpeta vigilada). Antes iba a la consola oculta del
     * launcher y el motivo de un FAILED se perdía.
     */
    public Resultado ejecutar(BatchType type, Path file) {
        String jar = properties.batches().get(type.key());
        if (jar == null || jar.isBlank()) throw new IllegalArgumentException("No JAR configured for " + type.key());
        try {
            Path logs = carpetaLogs();
            Files.createDirectories(logs);
            Path salida = logs.resolve(type.key() + "_" + file.getFileName() + ".log");
            ProcessBuilder builder = new ProcessBuilder(List.of(javaCommand(), "-jar", jar, "--file=" + file.toAbsolutePath().normalize()))
                    .redirectErrorStream(true).redirectOutput(salida.toFile());
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
            log.info("event=batch_process_finished type={} file={} exitCode={} log={}", type, file, exit, salida);
            if (exit == 0) return new Resultado(true, null);
            return new Resultado(false, motivo(Files.readAllLines(salida, StandardCharsets.UTF_8), exit, salida));
        } catch (IOException e) { throw new IllegalStateException("Cannot start batch " + type.key(), e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Batch interrupted", e); }
    }

    private Path carpetaLogs() {
        String env = System.getenv("BATCH_LOG_DIR");
        if (env != null && !env.isBlank()) return Path.of(env);
        Path vigilada = properties.watchDirectory().toAbsolutePath().normalize();
        return (vigilada.getParent() != null ? vigilada.getParent() : vigilada).resolve("batch-logs");
    }

    /**
     * Motivo legible de un batch fallido: la última causa raíz ({@code Caused by:}) o, si no
     * hay, la última línea de excepción o ERROR; recortado a 500 caracteres y con el log.
     */
    static String motivo(List<String> lineas, int exit, Path salida) {
        String elegido = null;
        for (String l : lineas) if (l.startsWith("Caused by:")) elegido = l;
        if (elegido == null)
            for (String l : lineas) if (l.matches("^[\\w.$]+(Exception|Error)\\b.*") || l.contains(" ERROR ")) elegido = l;
        String texto = elegido == null ? "exit code " + exit : elegido.trim();
        if (texto.length() > 500) texto = texto.substring(0, 500);
        return texto + " (log: " + salida + ")";
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
