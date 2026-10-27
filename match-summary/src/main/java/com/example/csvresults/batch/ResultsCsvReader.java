package com.example.csvstatsmatchbatch.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;

public class ResultsCsvReader extends AbstractItemCountingItemStreamItemReader<ResultRow> {
    private final CsvInput input; private final String path; private final String checksum;
    private CSVParser parser; private Iterator<CSVRecord> records; private String matchId;
    public ResultsCsvReader(CsvInput input, String path, String checksum) { this.input = input; this.path = path; this.checksum = checksum; setName("resultsCsvReader"); }
    @Override protected void doOpen() throws IOException {
        Path real = input.validate(path); if (!checksum.equals(CsvInput.checksum(real))) throw new IllegalArgumentException("CSV changed since job launch");
        String name=real.getFileName().toString(); matchId=name.matches("(?i)^MATCH_SUMMARY_.+\\.csv$")?name.substring(14,name.length()-4):name.substring(name.indexOf('_',14)+1,name.length()-4); parser = CsvInput.open(real); records = parser.iterator();
    }
    @Override protected ResultRow doRead() {
        if (!records.hasNext()) return null;
        CSVRecord first = records.next();
        if (first.size() != 5) return ResultRow.parse(first, matchId);
        if (!records.hasNext()) throw new IllegalArgumentException("SCORE_SUMMARY requires home and away rows");
        return ResultRow.fromScoreSummary(first, records.next(), matchId);
    }
    @Override protected void jumpToItem(int count) { for (int i = 0; i < count; i++) { if (!records.hasNext()) throw new IllegalArgumentException("CSV is shorter than its checkpoint"); CSVRecord r=records.next(); if (r.size()==5 && records.hasNext()) records.next(); } }
    @Override protected void doClose() throws IOException { if (parser != null) parser.close(); }
}
