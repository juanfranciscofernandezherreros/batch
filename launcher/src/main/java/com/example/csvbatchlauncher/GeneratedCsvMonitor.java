package com.example.csvbatchlauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GeneratedCsvMonitor {
    private static final Logger log = LoggerFactory.getLogger(GeneratedCsvMonitor.class);

    private final JdbcTemplate jdbc;
    private final Path directory;

    public GeneratedCsvMonitor(
            JdbcTemplate jdbc,
            @Value("${app.launcher.generated-files-directory:C:/data/basketball/competitions}") Path directory) {
        this.jdbc = jdbc;
        this.directory = directory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        scan();
    }

    @Scheduled(fixedDelayString = "${app.launcher.generated-files-poll-interval:5s}")
    public void scan() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        AtomicInteger recorded = new AtomicInteger();
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .filter(path -> BatchType.from(path).isPresent())
                    .forEach(path -> {
                        if (upsert(path)) {
                            recorded.incrementAndGet();
                        }
                    });
            log.info("event=generated_csv_scan_completed directory={} files={}", directory, recorded.get());
        } catch (IOException exception) {
            log.warn("event=generated_csv_scan_failed directory={}", directory, exception);
        }
    }

    private boolean upsert(Path file) {
        try {
            Path normalized = file.toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(normalized, BasicFileAttributes.class);
            Path relative = directory.toAbsolutePath().normalize().relativize(normalized);
            String country = relative.getNameCount() >= 3 ? relative.getName(0).toString() : null;
            String competition = relative.getNameCount() >= 2
                    ? relative.getName(relative.getNameCount() >= 3 ? 1 : 0).toString()
                    : null;
            String matchId = relative.getNameCount() >= 4 ? relative.getName(2).toString() : null;
            String dataset = BatchType.from(normalized).orElseThrow().key();
            Instant modifiedAt = attributes.lastModifiedTime().toInstant();

            jdbc.update("""
                    INSERT INTO csv_generated_file(file_path, file_name, dataset, country, competition, match_id, size_bytes, modified_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(file_path) DO UPDATE SET
                        file_name=EXCLUDED.file_name,
                        dataset=EXCLUDED.dataset,
                        country=EXCLUDED.country,
                        competition=EXCLUDED.competition,
                        match_id=EXCLUDED.match_id,
                        size_bytes=EXCLUDED.size_bytes,
                        modified_at=EXCLUDED.modified_at,
                        last_seen_at=CURRENT_TIMESTAMP
                    """,
                    normalized.toString(), normalized.getFileName().toString(), dataset, country, competition, matchId,
                    attributes.size(), Timestamp.from(modifiedAt));
            return true;
        } catch (IOException | RuntimeException exception) {
            log.warn("event=generated_csv_record_failed file={}", file, exception);
            return false;
        }
    }
}
