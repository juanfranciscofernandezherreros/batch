package com.example.csvextra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExtraCsvImporter implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Path allowedRoot;

    public ExtraCsvImporter(JdbcTemplate jdbc, @Value("${app.csv.allowed-root}") String allowedRoot) {
        this.jdbc = jdbc;
        this.allowedRoot = Path.of(allowedRoot).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String fileArgument = args.getOptionValues("file") == null ? null : args.getOptionValues("file").getFirst();
        if (fileArgument == null || fileArgument.isBlank()) throw new IllegalArgumentException("--file is required");
        Path file = Path.of(fileArgument).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !file.startsWith(allowedRoot)) throw new IllegalArgumentException("CSV path is outside CSV_ALLOWED_ROOT");
        String name = file.getFileName().toString().toUpperCase(Locale.ROOT);
        if (name.startsWith("MATCH_DETAILS_")) importMatchDetails(file);
        else if (name.startsWith("H2H_")) importH2h(file);
        else if (name.startsWith("ODDS_")) importOdds(file);
        else throw new IllegalArgumentException("Unsupported extra CSV: " + file.getFileName());
    }

    private void importMatchDetails(Path file) throws IOException {
        List<Object[]> batch = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader)) {
            require(parser, "view", "record_type", "label", "value", "link_url", "source_url");
            for (CSVRecord row : parser) {
                String source = text(row, "source_url");
                String matchId = matchId(source, file);
                String hash = hash(row.toString());
                batch.add(new Object[]{matchId, text(row,"view"), text(row,"record_type"), text(row,"label"), text(row,"value"), text(row,"link_url"), source, file.toString(), hash});
            }
        }
        jdbc.batchUpdate("""
                    INSERT INTO match_details.match_details(match_id,view_name,record_type,label,value_text,link_url,source_url,source_file,row_hash)
                    VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT (match_id,row_hash) DO UPDATE SET value_text=EXCLUDED.value_text, link_url=EXCLUDED.link_url, imported_at=CURRENT_TIMESTAMP
                    """, batch);
    }

    private void importH2h(Path file) throws IOException {
        List<Object[]> batch = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader)) {
            require(parser, "match_id", "section", "date", "competition", "home_team", "away_team", "note", "home_score", "away_score", "result_for_section_team", "source_url");
            for (CSVRecord row : parser) {
                String matchId = text(row,"match_id");
                batch.add(new Object[]{matchId, text(row,"section"), text(row,"date"), text(row,"competition"), text(row,"home_team"), text(row,"away_team"), text(row,"note"), text(row,"home_score"), text(row,"away_score"), text(row,"result_for_section_team"), text(row,"source_url"), file.toString(), hash(row.toString())});
            }
        }
        jdbc.batchUpdate("""
                    INSERT INTO h2h.h2h(match_id,section,event_date,competition,home_team,away_team,note,home_score,away_score,result_for_section_team,source_url,source_file,row_hash)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (match_id,row_hash) DO UPDATE SET note=EXCLUDED.note, imported_at=CURRENT_TIMESTAMP
                    """, batch);
    }

    private void importOdds(Path file) throws IOException {
        String marketType = file.getFileName().toString().split("_", 3)[1].toLowerCase(Locale.ROOT);
        List<Object[]> batch = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file); var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get().parse(reader)) {
            require(parser, "match_id", "market", "period", "bookmaker", "bookmaker_id", "line", "home_line", "away_line", "home_odds", "draw_odds", "away_odds", "over_odds", "under_odds", "raw_text", "source_url");
            for (CSVRecord row : parser) {
                batch.add(new Object[]{text(row,"match_id"), marketType, text(row,"market"), text(row,"period"), text(row,"bookmaker"), text(row,"bookmaker_id"), text(row,"line"), text(row,"home_line"), text(row,"away_line"), text(row,"home_odds"), text(row,"draw_odds"), text(row,"away_odds"), text(row,"over_odds"), text(row,"under_odds"), text(row,"raw_text"), text(row,"source_url"), file.toString(), hash(row.toString())});
            }
        }
        jdbc.batchUpdate("""
                    INSERT INTO odds.odds(match_id,market_type,market,period,bookmaker,bookmaker_id,line,home_line,away_line,home_odds,draw_odds,away_odds,over_odds,under_odds,raw_text,source_url,source_file,row_hash)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (match_id,market_type,row_hash) DO UPDATE SET raw_text=EXCLUDED.raw_text, imported_at=CURRENT_TIMESTAMP
                    """, batch);
    }

    private static void require(org.apache.commons.csv.CSVParser parser, String... names) {
        var headers = parser.getHeaderNames();
        for (String name : names) if (!headers.contains(name)) throw new IllegalArgumentException("Missing CSV column: " + name);
    }

    private static String text(CSVRecord row, String name) { return row.isMapped(name) ? row.get(name) : ""; }
    private static String matchId(String source, Path file) {
        int marker = source.indexOf("mid=");
        if (marker >= 0) { String value = source.substring(marker + 4); int amp = value.indexOf('&'); return amp >= 0 ? value.substring(0, amp) : value; }
        String[] parts = file.getFileName().toString().split("_");
        return parts.length > 2 ? parts[parts.length - 1].replaceFirst("(?i)\\.CSV$", "") : "unknown";
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
