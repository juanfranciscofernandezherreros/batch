package com.example.csvstatsmatchbatch.batch;
import org.springframework.batch.item.Chunk; import org.springframework.batch.item.ItemWriter; import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Types;
public class ResultsWriter implements ItemWriter<ResultRow> {
 static final String SQL="INSERT INTO match_summary (match_id,source_event_id,date,home_name,home_image,away_name,away_image,result_home,result_away) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(match_id) DO UPDATE SET source_event_id=EXCLUDED.source_event_id,date=EXCLUDED.date,home_name=EXCLUDED.home_name,home_image=EXCLUDED.home_image,away_name=EXCLUDED.away_name,away_image=EXCLUDED.away_image,result_home=EXCLUDED.result_home,result_away=EXCLUDED.result_away";
 static final String RESULTS_SQL="""
     INSERT INTO results.results (match_id, source_event_id, home_team, away_team, home_score, away_score,
       home_score1, home_score2, home_score3, home_score4, home_score5,
       away_score1, away_score2, away_score3, away_score4, away_score5,
       event_time, event_datetime, country, competition, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS timestamptz), ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT (match_id) DO UPDATE SET
       home_team=COALESCE(EXCLUDED.home_team, results.results.home_team),
       away_team=COALESCE(EXCLUDED.away_team, results.results.away_team),
       event_time=COALESCE(NULLIF(results.results.event_time, ''), EXCLUDED.event_time),
       event_datetime=COALESCE(results.results.event_datetime, EXCLUDED.event_datetime),
       country=COALESCE(NULLIF(results.results.country, ''), EXCLUDED.country),
       competition=COALESCE(NULLIF(results.results.competition, ''), EXCLUDED.competition),
       home_score=COALESCE(results.results.home_score, EXCLUDED.home_score),
       away_score=COALESCE(results.results.away_score, EXCLUDED.away_score),
       home_score1=COALESCE(results.results.home_score1, EXCLUDED.home_score1),
       home_score2=COALESCE(results.results.home_score2, EXCLUDED.home_score2),
       home_score3=COALESCE(results.results.home_score3, EXCLUDED.home_score3),
       home_score4=COALESCE(results.results.home_score4, EXCLUDED.home_score4),
       home_score5=COALESCE(results.results.home_score5, EXCLUDED.home_score5),
       away_score1=COALESCE(results.results.away_score1, EXCLUDED.away_score1),
       away_score2=COALESCE(results.results.away_score2, EXCLUDED.away_score2),
       away_score3=COALESCE(results.results.away_score3, EXCLUDED.away_score3),
       away_score4=COALESCE(results.results.away_score4, EXCLUDED.away_score4),
       away_score5=COALESCE(results.results.away_score5, EXCLUDED.away_score5),
       updated_at=CURRENT_TIMESTAMP
     """;
 private final JdbcTemplate jdbc; private final String source; public ResultsWriter(JdbcTemplate j,String s){jdbc=j;source=s;}
 public void write(Chunk<? extends ResultRow> c){
  jdbc.batchUpdate(SQL,c.getItems(),c.size(),(s,r)->{s.setObject(1,r.matchId());s.setObject(2,"csv-"+source+"-"+r.recordNumber());for(int i=0;i<7;i++){String value=r.values()[i];if(i==0){s.setObject(i+3,value,Types.DATE);}else{s.setObject(i+3,value);}}});
  jdbc.batchUpdate(RESULTS_SQL,c.getItems(),c.size(),(s,r)->{
   String[] v=r.values(); s.setObject(1,r.matchId()); s.setObject(2,"csv-"+source+"-"+r.recordNumber());
   s.setObject(3,v[1]); s.setObject(4,v[3]); s.setObject(5,integer(v[5])); s.setObject(6,integer(v[6]));
   for(int i=7;i<17;i++) s.setObject(i,integer(v[i]));
   s.setObject(17,v[17]); s.setObject(18,v[18]); s.setObject(19,v[19]); s.setObject(20,v[20]);
  });
 }
 private static Integer integer(String value){return value==null||value.isBlank()?null:Integer.valueOf(value);}
}
