package com.example.csvpointbypointbatch.batch;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CsvCommand implements ApplicationRunner {
    private final ImportService imports; public CsvCommand(ImportService imports) { this.imports = imports; }
    @Override public void run(ApplicationArguments args) throws Exception {
        String file = option(args, "file"), directory = option(args, "directory");
        boolean hasExactlyOneInput = (file == null) != (directory == null);
        if (!hasExactlyOneInput) throw new IllegalArgumentException("Use exactly one of --file=<CSV> or --directory=<directory>");
        boolean hasFileInput = file != null;
        if (hasFileInput) { imports.run(file, option(args, "source-event-id")); return; }
        try (var paths = java.nio.file.Files.walk(java.nio.file.Path.of(directory))) {
            paths.filter(java.nio.file.Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")).sorted().forEach(p -> { try { imports.run(p.toString(), null); } catch (Exception e) { throw new IllegalStateException("Batch failed for " + p, e); } });
        }
    }
    private static String option(ApplicationArguments args, String key) { var values = args.getOptionValues(key); boolean hasNoValues = values == null; if (hasNoValues) return null; boolean hasExactlyOneNonBlankValue = values.size() == 1 && !values.getFirst().isBlank(); if (!hasExactlyOneNonBlankValue) throw new IllegalArgumentException("Expected one value for --" + key); return values.getFirst(); }
}
