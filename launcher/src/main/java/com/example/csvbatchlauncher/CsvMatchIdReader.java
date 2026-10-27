package com.example.csvbatchlauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class CsvMatchIdReader {
    public String read(Path file) {
        try (var reader = Files.newBufferedReader(file);
                var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            boolean hasMatchIdHeader = parser.getHeaderMap().containsKey("match_id");
            if (!hasMatchIdHeader) {
                throw new IllegalArgumentException("CSV is missing the match_id column");
            }
            var records = parser.iterator();
            boolean hasRecord = records.hasNext();
            if (!hasRecord) {
                throw new IllegalArgumentException("CSV contains no records to validate");
            }
            CSVRecord firstRecord = records.next();
            String matchId = firstRecord.get("match_id").trim();
            boolean hasMatchId = !matchId.isEmpty();
            if (!hasMatchId) {
                throw new IllegalArgumentException("CSV has a blank match_id");
            }
            return matchId;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read CSV prerequisite data", exception);
        }
    }
}
