package com.example.csvfixtures.batch;
import com.fernandez.basketball.csv.core.SafeCsvPathValidator;
import java.io.IOException; import java.nio.file.Files; import java.nio.file.Path; import java.security.DigestInputStream; import java.security.MessageDigest; import java.security.NoSuchAlgorithmException; import java.util.HexFormat; import java.util.List;
import org.apache.commons.csv.CSVFormat; import org.apache.commons.csv.CSVParser; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;
@Component
public class CsvInput {
    static final List<String> HEADER = List.of("match_id", "event_time", "home_team", "away_team", "event_datetime"); static final List<String> LEGACY_HEADER = List.of("matchId", "eventTime", "homeTeam", "awayTeam"); private final SafeCsvPathValidator paths; private final long maxFileSize;
    public CsvInput(@Value("${app.csv.allowed-root}") String root, @Value("${app.processing.max-file-size-bytes}") long maxFileSize) { paths = new SafeCsvPathValidator(root); this.maxFileSize = maxFileSize; }
    public Path validate(String path) {
        final Path real; try { real = paths.validate(path); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV path", e); }
        String name = real.getFileName().toString();
        if (!name.matches("(?i)^FIXTURES_(?:\\d{14}_[^_]+_.+|[^_]+_.+)\\.csv$")) throw new IllegalArgumentException("FIXTURES requires FIXTURES_<timestamp>_<country>_<competition>.csv or legacy FIXTURES_<country>_<competition>.csv");
        try (var reader = Files.newBufferedReader(real); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) { var header=parser.getHeaderNames(); if (!HEADER.equals(header) && !LEGACY_HEADER.equals(header)) throw new IllegalArgumentException("Invalid FIXTURES header: expected " + HEADER + " or " + LEGACY_HEADER); }
        catch (IOException e) { throw new IllegalArgumentException("Cannot read CSV", e); }
        try { if (Files.size(real) > maxFileSize) throw new IllegalArgumentException("FIXTURES file exceeds configured size limit " + maxFileSize); } catch (IOException e) { throw new IllegalArgumentException("Cannot inspect CSV", e); }
        return real;
    }
    public static String checksum(Path path) { try { var digest = MessageDigest.getInstance("SHA-256"); try (var input = new DigestInputStream(Files.newInputStream(path), digest)) { input.transferTo(java.io.OutputStream.nullOutputStream()); } return HexFormat.of().formatHex(digest.digest()); } catch (IOException | NoSuchAlgorithmException e) { throw new IllegalArgumentException("Cannot hash CSV", e); } }
    static CSVParser open(Path path) throws IOException { var reader = Files.newBufferedReader(path); try { return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader); } catch (IOException | RuntimeException e) { reader.close(); throw e; } }
}
