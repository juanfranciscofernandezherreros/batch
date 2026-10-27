package com.example.csvpointbypointbatch.batch;
import org.apache.commons.csv.CSVRecord;
public record ResultRow(long recordNumber,String[] v){static ResultRow parse(CSVRecord r){if(r.size()!=13&&r.size()!=15)throw new IllegalArgumentException("POINT_BY_POINT requires 13 or 15 columns");String[]x=new String[13];for(int i=0;i<13;i++)x[i]=r.get(i).isBlank()?null:r.get(i);for(int i:new int[]{0,1,2,3,4,5,6,7,11,12})if(x[i]==null)throw new IllegalArgumentException("Required point-by-point column is blank");return new ResultRow(r.getRecordNumber(),x);}}
