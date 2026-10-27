package com.example.csvstatsmatchbatch.batch;
import org.apache.commons.csv.CSVRecord;
public record ResultRow(long recordNumber, String matchId, String[] values) {
    static ResultRow fromScoreSummary(CSVRecord home, CSVRecord away, String matchId) {
        if (home.size() != 5 || away.size() != 5) throw new IllegalArgumentException("SCORE_SUMMARY rows require 5 columns");
        if (!"home".equalsIgnoreCase(home.get(1)) || !"away".equalsIgnoreCase(away.get(1)))
            throw new IllegalArgumentException("SCORE_SUMMARY must contain one home row followed by one away row");
        String rowMatchId = clean(home.get(0));
        if (rowMatchId == null || !rowMatchId.equals(clean(away.get(0))))
            throw new IllegalArgumentException("SCORE_SUMMARY home and away rows require the same match_id");
        // A consolidated SCORE_SUMMARY file is named with the aggregate suffix "all".
        // In that format, each home/away pair owns its identity in column match_id.
        String effectiveMatchId = "all".equalsIgnoreCase(matchId) ? rowMatchId : matchId;
        if (!effectiveMatchId.equals(rowMatchId))
            throw new IllegalArgumentException("SCORE_SUMMARY row match_id does not match the filename match_id");
        String[] v = new String[7];
        v[1] = clean(home.get(2)); v[2] = clean(home.get(3));
        v[3] = clean(away.get(2)); v[4] = clean(away.get(3));
        v[5] = clean(home.get(4)); v[6] = clean(away.get(4));
        return new ResultRow(home.getRecordNumber(), effectiveMatchId, v);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value; }
    static ResultRow parse(CSVRecord r, String matchId) { if (r.size()!=7) throw new IllegalArgumentException("MATCH_SUMMARY requires 7 columns"); String[] v=new String[7]; for(int i=0;i<7;i++) v[i]=r.get(i).isBlank()?null:r.get(i); return new ResultRow(r.getRecordNumber(),matchId,v); }
}
