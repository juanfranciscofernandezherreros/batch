package com.example.csvfixtures.batch;
import org.apache.commons.csv.CSVRecord;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
public record FixtureRow(long recordNumber, String matchId, String country, String competition, LocalTime eventTime, OffsetDateTime eventDatetime, String homeTeam, String awayTeam) {
    private static final Pattern DATE_LIKE_TEAM = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(?:[T ].*)?$");
    static FixtureRow parse(CSVRecord r, String country, String competition) { if (r.size() == 1 && r.get(0).isBlank()) return null; if (r.size() != 5 && r.size() != 4) throw new IllegalArgumentException("Invalid FIXTURES column count recordNumber=" + r.getRecordNumber()); boolean extended = r.size() == 5; return new FixtureRow(r.getRecordNumber(), required(r, 0, "match_id"), country, competition, eventTime(r), extended ? eventDatetime(r) : null, team(r, 2, "home_team"), team(r, 3, "away_team")); }
    private static String required(CSVRecord r, int i, String name) { String v = r.get(i); if (v.isBlank()) throw new IllegalArgumentException("Missing required field recordNumber=" + r.getRecordNumber() + " field=" + name); return v; }
    private static String optional(String v) { return v == null || v.isBlank() ? null : v; }
    private static String team(CSVRecord r, int i, String name) { String value = optional(r.get(i)); if (value != null && DATE_LIKE_TEAM.matcher(value).matches()) throw new IllegalArgumentException("Invalid FIXTURES team field recordNumber=" + r.getRecordNumber() + " field=" + name + ": value looks like a date"); return value; }
    private static LocalTime eventTime(CSVRecord r) { String value = optional(r.get(1)); if (value == null) return null; value=value.replaceFirst("^.*\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?).*$", "$1"); try { return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME); } catch (DateTimeParseException e) { throw new IllegalArgumentException("Invalid event_time recordNumber=" + r.getRecordNumber() + ": expected HH:mm[:ss]", e); } }
    private static OffsetDateTime eventDatetime(CSVRecord r) { String value = optional(r.get(4)); if (value == null) return null; try { return OffsetDateTime.parse(value); } catch (DateTimeParseException e) { throw new IllegalArgumentException("Invalid event_datetime recordNumber=" + r.getRecordNumber(), e); } }
    public String sourceEventId(String fileName) { return "csv-row-" + Integer.toHexString((fileName + "|" + recordNumber + "|" + matchId).hashCode()) + "-" + recordNumber; }
}
