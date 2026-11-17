package com.example.csvstatsplayerbatch.batch;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class ResultsWriter implements ItemWriter<ResultRow> {
    // La clave es (match_id, team, player_key); player_key es player_id o, si falta, name
    // (columna generada, migración V2). Así dos "Williams J." con distinto id no chocan.
    static final String SQL = "INSERT INTO stats_player (id,match_id,name,team,pts,reb,ast,min,fgm,fga,twopm,twopa,threepm,threepa,ftm,fta,plus_minus,offensive_rebounds,dr,pf,st,turnovers,bs,ba,tfs,source_event_id,player_id) VALUES (nextval('stats_player_seq'),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(match_id,team,player_key) DO UPDATE SET name=EXCLUDED.name,pts=EXCLUDED.pts,reb=EXCLUDED.reb,ast=EXCLUDED.ast,min=EXCLUDED.min,fgm=EXCLUDED.fgm,fga=EXCLUDED.fga,twopm=EXCLUDED.twopm,twopa=EXCLUDED.twopa,threepm=EXCLUDED.threepm,threepa=EXCLUDED.threepa,ftm=EXCLUDED.ftm,fta=EXCLUDED.fta,plus_minus=EXCLUDED.plus_minus,offensive_rebounds=EXCLUDED.offensive_rebounds,dr=EXCLUDED.dr,pf=EXCLUDED.pf,st=EXCLUDED.st,turnovers=EXCLUDED.turnovers,bs=EXCLUDED.bs,ba=EXCLUDED.ba,tfs=EXCLUDED.tfs,source_event_id=EXCLUDED.source_event_id,updated_at=CURRENT_TIMESTAMP";
    // Filas cargadas antes de tener player_id: si el CSV nuevo trae ids, sobran (si no, el
    // partido quedaría con cada jugador dos veces: una por nombre y otra por id).
    static final String BORRAR_SIN_ID = "DELETE FROM stats_player WHERE match_id=? AND player_id IS NULL";

    private final JdbcTemplate jdbc;
    private final String source;

    public ResultsWriter(JdbcTemplate j, String s) { jdbc = j; source = s; }

    public void write(Chunk<? extends ResultRow> c) {
        Set<String> partidosConId = new LinkedHashSet<>();
        for (ResultRow r : c.getItems()) if (r.playerId() != null) partidosConId.add(r.values()[0]);
        for (String matchId : partidosConId) jdbc.update(BORRAR_SIN_ID, matchId);
        jdbc.batchUpdate(SQL, c.getItems(), c.size(), (s, r) -> {
            for (int i = 0; i < ResultRow.COLUMNAS; i++) {
                String value = r.values()[i];
                if (isNumericColumn(i) && value != null) s.setObject(i + 1, Integer.valueOf(value));
                else s.setObject(i + 1, value);
            }
            s.setObject(25, "csv-" + source + "-" + r.recordNumber());
            s.setObject(26, r.playerId());
        });
    }

    private static boolean isNumericColumn(int i) { return i >= 3 && i != 6; }
}
