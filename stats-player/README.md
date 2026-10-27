# csv-stats-player-batch

Batch autónomo sin Kafka para `player_stats.csv` o `PLAYER_STATS_<timestamp>_<matchId>.csv`. Lee 24 columnas y hace upsert por `(match_id, name, team)` en `stats_player`.

Ejecutar con `mvn -B spring-boot:run "-Dspring-boot.run.arguments=--file=C:\data\csv\player_stats.csv"` o `--directory`.
