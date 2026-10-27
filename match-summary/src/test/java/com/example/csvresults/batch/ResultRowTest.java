package com.example.csvstatsmatchbatch.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

class ResultRowTest {
    private static org.apache.commons.csv.CSVRecord[] scoreRecords(String firstId, String secondId) throws Exception {
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(
                firstId + ",home,Home,https://example.com/home,80\n"
                        + secondId + ",away,Away,https://example.com/away,75\n"))) {
            return parser.getRecords().toArray(org.apache.commons.csv.CSVRecord[]::new);
        }
    }

    @Test
    void usesRowMatchIdForConsolidatedScoreSummary() throws Exception {
        var records = scoreRecords("QFO2xa3N", "QFO2xa3N");
        assertEquals("QFO2xa3N", ResultRow.fromScoreSummary(records[0], records[1], "all").matchId());
    }

    @Test
    void rejectsMismatchedScoreSummaryPair() throws Exception {
        var records = scoreRecords("QFO2xa3N", "other");
        assertThrows(IllegalArgumentException.class,
                () -> ResultRow.fromScoreSummary(records[0], records[1], "all"));
    }
}
