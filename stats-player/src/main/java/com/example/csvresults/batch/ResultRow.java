package com.example.csvstatsplayerbatch.batch;
import org.apache.commons.csv.CSVRecord;
public record ResultRow(long recordNumber, String[] values) {
 static ResultRow parse(CSVRecord r){if(r.size()!=24)throw new IllegalArgumentException("PLAYER_STATS requires 24 columns");String[]v=new String[24];for(int i=0;i<24;i++)v[i]=r.get(i).isBlank()||r.get(i).equals("-")?null:r.get(i);if(v[0]==null||v[1]==null||v[2]==null)throw new IllegalArgumentException("match_id, Player and Team are required");return new ResultRow(r.getRecordNumber(),v);}
}
