package com.example.csvstatsplayerbatch.batch;

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
    static final List<String> HEADER = List.of("match_id","Player","Team","PTS","REB","AST","MIN","FGM","FGA","2PM","2PA","3PM","3PA","FTM","FTA","+/-","OR","DR","PF","ST","TO","BS","BA","TFS");
    private final SafeCsvPathValidator paths;
    public CsvInput(@Value("${app.csv.allowed-root}") String root) { paths = new SafeCsvPathValidator(root); }

    public Path validate(String path) {
        final Path real;
        try { real = paths.validate(path); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV path", e); }
        String name = real.getFileName().toString();
        if (!(name.equalsIgnoreCase("player_stats.csv") || name.matches("(?i)^PLAYER_STATS_\\d{14}_.+\\.csv$"))) {
            throw new IllegalArgumentException("PLAYER_STATS requires player_stats.csv or PLAYER_STATS_<timestamp>_<matchId>.csv");
        }
        try (var reader = Files.newBufferedReader(real); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            var header = parser.getHeaderNames();
            if (!header.isEmpty() && header.getFirst().startsWith("\uFEFF")) header.set(0, header.getFirst().substring(1));
            if (!HEADER.equals(header)) throw new IllegalArgumentException("Invalid PLAYER_STATS header: expected " + HEADER + " but got " + header);
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read CSV", e); }
        return real;
    }

    public static String checksum(Path path) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) { input.transferTo(java.io.OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalArgumentException("Cannot hash CSV", e); }
    }
    static CSVParser open(Path path) throws IOException {
        var reader = Files.newBufferedReader(path);
        try { return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader); }
        catch (IOException | RuntimeException e) { reader.close(); throw e; }
    }
}
