package com.example.csvbatchlauncher;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResultsMatchReadinessStrategy implements BatchReadinessStrategy {
    private final JdbcTemplate jdbc;
    private final LauncherProperties properties;
    private final CsvMatchIdReader matchIdReader;

    public ResultsMatchReadinessStrategy(JdbcTemplate jdbc, LauncherProperties properties, CsvMatchIdReader matchIdReader) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.matchIdReader = matchIdReader;
    }

    @Override
    public boolean supports(BatchType type) {
        return type.requiresResultsMatch();
    }

    @Override
    public BatchReadiness assess(Path file) {
        String matchId = matchIdReader.read(file);
        boolean hasResultsMatch = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + resultsTableName() + " WHERE match_id = ?)", Boolean.class, matchId));
        return hasResultsMatch ? BatchReadiness.available()
                : BatchReadiness.blocked("RESULTS has not been imported for match_id=" + matchId);
    }

    private String resultsTableName() {
        Map<String, String> schemas = properties.batchDbSchemas();
        String schema = schemas.getOrDefault(BatchType.RESULTS.key(), "results");
        boolean hasSafeSchemaName = schema.matches("[A-Za-z_][A-Za-z0-9_]*");
        if (!hasSafeSchemaName) {
            throw new IllegalStateException("Invalid results database schema configuration");
        }
        return '"' + schema + '"' + ".results";
    }
}
