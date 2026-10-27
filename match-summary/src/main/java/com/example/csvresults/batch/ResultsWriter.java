package com.example.csvstatsmatchbatch.batch;
import org.springframework.batch.item.Chunk; import org.springframework.batch.item.ItemWriter; import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Types;
public class ResultsWriter implements ItemWriter<ResultRow> {
 static final String SQL="INSERT INTO match_summary (match_id,source_event_id,date,home_name,home_image,away_name,away_image,result_home,result_away) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(match_id) DO UPDATE SET source_event_id=EXCLUDED.source_event_id,date=EXCLUDED.date,home_name=EXCLUDED.home_name,home_image=EXCLUDED.home_image,away_name=EXCLUDED.away_name,away_image=EXCLUDED.away_image,result_home=EXCLUDED.result_home,result_away=EXCLUDED.result_away";
 private final JdbcTemplate jdbc; private final String source; public ResultsWriter(JdbcTemplate j,String s){jdbc=j;source=s;}
 public void write(Chunk<? extends ResultRow> c){jdbc.batchUpdate(SQL,c.getItems(),c.size(),(s,r)->{s.setObject(1,r.matchId());s.setObject(2,"csv-"+source+"-"+r.recordNumber());for(int i=0;i<7;i++){String value=r.values()[i];if(i==0){s.setObject(i+3,value,Types.DATE);}else{s.setObject(i+3,value);}}});}
}
