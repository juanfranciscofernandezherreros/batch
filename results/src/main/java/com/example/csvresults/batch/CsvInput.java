package com.example.csvresults.batch;

import com.fernandez.basketball.csv.core.SafeCsvPathValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvInput {
    static final List<String> HEADER = List.of("matchId", "eventTime", "eventDatetime", "homeTeam", "awayTeam", "homeScore", "awayScore",
            "home_score1", "home_score2", "home_score3", "home_score4", "home_score5", "away_score1", "away_score2", "away_score3", "away_score4", "away_score5");
    static final List<String> CAMEL_CASE_HEADER = List.of("matchId", "eventTime", "eventDatetime", "homeTeam", "awayTeam", "homeScore", "awayScore",
            "homeScore1", "homeScore2", "homeScore3", "homeScore4", "homeScore5", "awayScore1", "awayScore2", "awayScore3", "awayScore4", "awayScore5");
    static final List<String> LEGACY_HEADER = List.of("matchId", "eventTime", "homeTeam", "awayTeam", "homeScore", "awayScore",
            "homeScore1", "homeScore2", "homeScore3", "homeScore4", "homeScore5", "awayScore1", "awayScore2", "awayScore3", "awayScore4", "awayScore5");
    private final SafeCsvPathValidator paths;
    public CsvInput(@Value("${app.csv.allowed-root}") String root) { paths = new SafeCsvPathValidator(root); }

    public Path validate(String path) {
        final Path real;
        try { real = paths.validate(path); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV path", e); }
        String name = real.getFileName().toString();
        boolean hasResultsFileName = name.matches("(?i)^RESULTS_[^_]+_[^_]+_.+\\.csv$");
        if (!hasResultsFileName) {
            throw new IllegalArgumentException("RESULTS requires RESULTS_<timestamp>_<country>_<competition>.csv");
        }
        try (var reader = Files.newBufferedReader(real); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            var header = new ArrayList<>(parser.getHeaderNames());
            header.replaceAll(value -> value.replace("\"", ""));
            boolean hasByteOrderMark = !header.isEmpty() && header.getFirst().startsWith("\uFEFF");
            if (hasByteOrderMark) header.set(0, header.getFirst().substring(1));
            boolean hasSupportedHeader = HEADER.equals(header) || CAMEL_CASE_HEADER.equals(header) || LEGACY_HEADER.equals(header);
            if (!hasSupportedHeader) {
                throw new IllegalArgumentException("Invalid RESULTS header: expected " + HEADER + " or legacy header but got " + header);
            }
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
