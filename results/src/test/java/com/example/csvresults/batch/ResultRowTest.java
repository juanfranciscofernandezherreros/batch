package com.example.csvresults.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

class ResultRowTest {
    @Test void parsesScoresAndEmptyOptionalFields() throws Exception {
        String row = "m1,,2026-10-15T21:00:00+02:00,Home,Away,80,75,20,18,22,20,0,19,17,21,18,0\n";
        var parser = CSVFormat.DEFAULT.parse(new StringReader(row));
        var result = ResultRow.parse(parser.iterator().next(), "ES", "ACB");
        assertEquals("m1", result.matchId());
        assertEquals("2026-10-15T21:00:00+02:00", result.eventDatetime());
        assertEquals(80, result.homeScore());
        assertEquals(0, result.homeScore5());
        assertEquals("ES", result.country());
    }

    @Test void rejectsMissingMatchId() throws Exception {
        var parser = CSVFormat.DEFAULT.parse(new StringReader(",,2026-10-15T21:00:00+02:00,Home,Away,80,75,20,18,22,20,0,19,17,21,18,0\n"));
        assertThrows(IllegalArgumentException.class, () -> ResultRow.parse(parser.iterator().next(), "ES", "ACB"));
    }

    @Test void parsesLegacyLayoutWithoutEventDatetime() throws Exception {
        var parser = CSVFormat.DEFAULT.parse(new StringReader("m2,19:00,Home,Away,80,75,20,18,22,20,0,19,17,21,18,0\n"));
        var result = ResultRow.parse(parser.iterator().next(), "IT", "lega-a");
        assertEquals("m2", result.matchId());
        assertNull(result.eventDatetime());
        assertEquals("Home", result.homeTeam());
        assertEquals(80, result.homeScore());
        assertEquals(0, result.awayScore5());
    }
}
