package com.example.csvpointbypointbatch.batch;

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
    static final List<String> HEADER = List.of("match_id", "record_type", "quarter", "sequence", "home_score", "away_score", "home_points_added", "away_points_added", "leader_side", "advantage", "advantage_direction", "home_is_winning", "away_is_winning");
    private final SafeCsvPathValidator paths;
    public CsvInput(@Value("${app.csv.allowed-root}") String root) { paths = new SafeCsvPathValidator(root); }

    public Path validate(String path) {
        final Path real;
        try { real = paths.validate(path); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV path", e); }
        String name = real.getFileName().toString();
        if (!name.matches("(?i)^POINT_BY_POINT_.+\\.csv$")) {
            throw new IllegalArgumentException("POINT_BY_POINT requires POINT_BY_POINT_<matchId>.csv");
        }
        try (var reader = Files.newBufferedReader(real); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            var header = parser.getHeaderNames();
            if (!header.isEmpty() && header.getFirst().startsWith("\uFEFF")) header.set(0, header.getFirst().substring(1));
            var extendedHeader = new java.util.ArrayList<>(HEADER);
            extendedHeader.add("link_url"); extendedHeader.add("source_url");
            if (!HEADER.equals(header) && !extendedHeader.equals(header)) throw new IllegalArgumentException("Invalid POINT_BY_POINT header: expected " + HEADER + " or extended header but got " + header);
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
