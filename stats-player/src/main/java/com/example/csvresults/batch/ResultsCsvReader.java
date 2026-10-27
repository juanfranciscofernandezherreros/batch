package com.example.csvstatsplayerbatch.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;

public class ResultsCsvReader extends AbstractItemCountingItemStreamItemReader<ResultRow> {
    private final CsvInput input; private final String path; private final String checksum;
    private CSVParser parser; private Iterator<CSVRecord> records; private String country; private String competition;
    public ResultsCsvReader(CsvInput input, String path, String checksum) { this.input = input; this.path = path; this.checksum = checksum; setName("resultsCsvReader"); }
    @Override protected void doOpen() throws IOException {
        Path real = input.validate(path); if (!checksum.equals(CsvInput.checksum(real))) throw new IllegalArgumentException("CSV changed since job launch");
        String[] metadata = real.getFileName().toString().replaceFirst("(?i)\\.csv$", "").split("_", 4);
        country = metadata[2]; competition = metadata[3]; parser = CsvInput.open(real); records = parser.iterator();
    }
    @Override protected ResultRow doRead() { return records.hasNext() ? ResultRow.parse(records.next()) : null; }
    @Override protected void jumpToItem(int count) { for (int i = 0; i < count; i++) { if (!records.hasNext()) throw new IllegalArgumentException("CSV is shorter than its checkpoint"); records.next(); } }
    @Override protected void doClose() throws IOException { if (parser != null) parser.close(); }
}
