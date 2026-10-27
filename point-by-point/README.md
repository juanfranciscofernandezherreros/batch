# csv-point-by-point-batch

Batch autónomo sin Kafka para `POINT_BY_POINT_*.csv`. Lee las 13 columnas del flujo point-by-point y hace upsert por `(match_id, quarter, sequence)` en `point_by_point_event`. La tabla `point_by_point_import` conserva el estado del import.

Ejecutar con `mvn -B spring-boot:run "-Dspring-boot.run.arguments=--file=C:\data\csv\POINT_BY_POINT_123.csv"` o `--directory`.
