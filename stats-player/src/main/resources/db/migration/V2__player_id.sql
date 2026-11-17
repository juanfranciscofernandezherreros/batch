-- Dos jugadores con el mismo nombre abreviado en un partido (Jalen y Jaylin Williams, los
-- dos "Williams J." en OKC) chocaban en la clave (match_id, name, team) y el lote entero
-- fallaba. La clave pasa a usar el id de Flashscore del jugador cuando el CSV lo trae.
ALTER TABLE stats_player ADD COLUMN IF NOT EXISTS player_id VARCHAR(64);

-- player_key: el id del jugador o, en filas antiguas sin id, su nombre.
ALTER TABLE stats_player ADD COLUMN IF NOT EXISTS player_key VARCHAR(255)
    GENERATED ALWAYS AS (COALESCE(player_id, name)) STORED;

ALTER TABLE stats_player DROP CONSTRAINT IF EXISTS uk_stats_player_match_player_team;
CREATE UNIQUE INDEX IF NOT EXISTS uk_stats_player_match_team_player
    ON stats_player (match_id, team, player_key);
