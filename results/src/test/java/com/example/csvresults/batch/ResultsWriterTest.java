package com.example.csvresults.batch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResultsWriterTest {
    @Test
    void upsertKeepsExistingEventTimeWhenTheNewRowHasNone() {
        String sql = ResultsWriter.UPSERT.replaceAll("\\s+", " ");
        assertTrue(sql.contains("event_time=COALESCE(EXCLUDED.event_time, results.event_time)"), sql);
        assertTrue(sql.contains("event_datetime=COALESCE(EXCLUDED.event_datetime, results.event_datetime)"), sql);
    }
}
