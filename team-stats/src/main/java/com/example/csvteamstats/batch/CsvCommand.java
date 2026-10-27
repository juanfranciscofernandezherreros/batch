package com.example.csvteamstats.batch;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.cli.enabled", havingValue = "true", matchIfMissing = true)
public class CsvCommand implements ApplicationRunner {
    private final ImportService imports;
    public CsvCommand(ImportService imports) { this.imports = imports; }

    @Override public void run(ApplicationArguments args) throws Exception {
        String file = option(args, "file");
        String directory = option(args, "directory");
        boolean hasExactlyOneInput = (file == null) != (directory == null);
        if (!hasExactlyOneInput) {
            throw new IllegalArgumentException("Use exactly one of --file=<absolute CSV path> or --directory=<absolute directory>");
        }
        boolean hasFileInput = file != null;
        if (hasFileInput) {
            imports.run(file, option(args, "source-event-id"), "csv", -1, -1);
            return;
        }
        var root = java.nio.file.Path.of(directory);
        try (var paths = java.nio.file.Files.walk(root)) {
            paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))
                    .sorted()
                    .forEach(path -> {
                        try { imports.run(path.toString(), "csv-sha256-" + CsvInput.checksum(path), "csv", -1, -1); }
                        catch (Exception exception) { throw new IllegalStateException("Batch failed for " + path, exception); }
                    });
        }
    }

    private static String option(ApplicationArguments args, String key) {
        var values = args.getOptionValues(key);
        boolean hasNoValues = values == null;
        if (hasNoValues) return null;
        boolean hasExactlyOneNonBlankValue = values.size() == 1 && !values.getFirst().isBlank();
        if (!hasExactlyOneNonBlankValue) throw new IllegalArgumentException("Expected one value for --" + key);
        return values.getFirst();
    }
}
