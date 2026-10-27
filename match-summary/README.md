# csv-stats-match-batch

Batch autónomo sin Kafka para `MATCH_SUMMARY_<matchId>.csv` y `SCORE_SUMMARY_<timestamp>_<matchId>.csv`. Conserva sólo metadatos, equipos y marcador final en `match_summary`; los parciales pertenecen exclusivamente a `results`.

Ejecutar con `mvn -B spring-boot:run "-Dspring-boot.run.arguments=--file=C:\data\csv\MATCH_SUMMARY_123.csv"` o `--directory`.
