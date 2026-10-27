package com.example.csvteamstats.batch;

import com.fernandez.basketball.csv.core.SafeCsvPathValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvInput {
    static final List<String> HEADER = List.of("match_id", "period", "category", "metric",
            "home_team", "home_value", "away_team", "away_value", "source_url");
    private final SafeCsvPathValidator paths;

    public CsvInput(@Value("${app.csv.allowed-root}") String root) { paths = new SafeCsvPathValidator(root); }

    public Path validate(String path) {
        final Path real;
        try { real = paths.validate(path); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV path", e); }
        String name = real.getFileName().toString();
        if (!name.equalsIgnoreCase("stats_all_periods.csv")
                && !name.matches("(?i)^STATS_ALL_PERIODS_\\d{14}_.+\\.csv$")) {
            throw new IllegalArgumentException("TEAM_STATS requires stats_all_periods.csv or STATS_ALL_PERIODS_<timestamp>_<matchId>.csv");
        }
        try (var reader = Files.newBufferedReader(real);
                var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            if (!HEADER.equals(parser.getHeaderNames())) throw new IllegalArgumentException("Invalid TEAM_STATS header");
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read CSV", e); }
        return real;
    }

    public static String checksum(Path path) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalArgumentException("Cannot hash CSV", e); }
    }

    static CSVParser open(Path path) throws IOException {
        var reader = Files.newBufferedReader(path);
        try {
            return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader);
        } catch (IOException | RuntimeException e) { reader.close(); throw e; }
    }
}
