package com.example.csvstatsmatchbatch.batch;

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
    static final List<String> HEADER = List.of("date", "homeName", "homeImage", "awayName", "awayImage", "resultHome", "resultAway");
    static final List<String> SCORE_HEADER = List.of("match_id", "side", "team", "team_url", "total",
            "home_score1", "home_score2", "home_score3", "home_score4", "home_score5",
            "away_score1", "away_score2", "away_score3", "away_score4", "away_score5");
    private final SafeCsvPathValidator paths;
    public CsvInput(@Value("${app.csv.allowed-root}") String root) { paths = new SafeCsvPathValidator(root); }

    public Path validate(String path) {
        final Path real;
        try { real = paths.validate(path); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV path", e); }
        String name = real.getFileName().toString();
        if (!name.matches("(?i)^(MATCH_SUMMARY_.+|SCORE_SUMMARY_\\d{14}_.+)\\.csv$")) {
            throw new IllegalArgumentException("MATCH_SUMMARY requires MATCH_SUMMARY_<matchId>.csv or SCORE_SUMMARY_<timestamp>_<matchId>.csv");
        }
        try (var reader = Files.newBufferedReader(real); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            var header = parser.getHeaderNames();
            if (!header.isEmpty() && header.getFirst().startsWith("\uFEFF")) header.set(0, header.getFirst().substring(1));
            // SCORE_SUMMARY puede traer columnas de identidad extra (eventTime,
            // eventDatetime, homeTeam, awayTeam, homeScore, awayScore, country,
            // competition) al final: basta con que empiece por las 15 originales.
            boolean scoreHeader = header.size() >= SCORE_HEADER.size()
                    && header.subList(0, SCORE_HEADER.size()).equals(SCORE_HEADER);
            if (!HEADER.equals(header) && !scoreHeader) throw new IllegalArgumentException("Invalid MATCH_SUMMARY header");
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
