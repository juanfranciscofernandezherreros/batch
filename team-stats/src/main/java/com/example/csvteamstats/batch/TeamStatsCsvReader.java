package com.example.csvteamstats.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;

/** Commons CSV preserves quoted commas, escaped quotes and multiline fields on restart. */
public class TeamStatsCsvReader extends AbstractItemCountingItemStreamItemReader<TeamStatsRow> {
    private final CsvInput input;
    private final String path;
    private final String expectedChecksum;
    private CSVParser parser;
    private Iterator<CSVRecord> records;

    public TeamStatsCsvReader(CsvInput input, String path, String expectedChecksum) {
        this.input = input;
        this.path = path;
        this.expectedChecksum = expectedChecksum;
        setName("teamStatsCsvReader");
    }

    @Override protected void doOpen() throws IOException {
        Path real = input.validate(path);
        if (!expectedChecksum.equals(CsvInput.checksum(real))) {
            throw new IllegalArgumentException("CSV changed since job launch; use an immutable input file");
        }
        parser = CsvInput.open(real);
        records = parser.iterator();
    }

    @Override protected TeamStatsRow doRead() {
        return records.hasNext() ? TeamStatsRow.parse(records.next()) : null;
    }

    @Override protected void jumpToItem(int count) {
        for (int i = 0; i < count; i++) {
            if (!records.hasNext()) throw new IllegalArgumentException("CSV is shorter than its checkpoint");
            records.next();
        }
    }

    @Override protected void doClose() throws IOException { if (parser != null) parser.close(); }
}
