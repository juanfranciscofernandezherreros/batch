# csv-batch-launcher

Proceso ligero que conecta `csv-watcher` con los batches CSV autónomos. Expone una API asíncrona, guarda las peticiones en PostgreSQL, ejecuta el JAR correspondiente y registra el resultado. No usa Kafka.

```text
csv-watcher -> POST /batch-launches -> cola PostgreSQL -> java -jar <batch>.jar --file=<ruta> -> PostgreSQL
```

Arranque:

```powershell
cd C:\Develop\basketball\csv-batch-launcher
mvn -B spring-boot:run
```

Configura las rutas de los JAR con `RESULTS_BATCH_JAR`, `FIXTURES_BATCH_JAR`, `TEAM_STATS_BATCH_JAR`, `MATCH_STATS_BATCH_JAR`, `PLAYER_STATS_BATCH_JAR` y `POINT_BY_POINT_BATCH_JAR`. La API devuelve `202 Accepted` y el worker recoge las peticiones según `WATCH_POLL_INTERVAL`. Consulta el estado con `GET /batch-launches/{requestId}`.

Para activar el modo batch del watcher configura `CSV_DELIVERY_MODE=batch` y `CSV_BATCH_LAUNCHER_URL=http://csv-batch-launcher:8088`. En este modo el watcher llama a la API y no publica Kafka. El escaneo interno del launcher queda desactivado para no detectar dos veces el mismo fichero.

Tabla principal de la cola: `batch_launch_request`. Los estados son `QUEUED`, `RUNNING`, `COMPLETED` y `FAILED`.

La conexión de la cola del launcher usa `DB_URL`, `DB_USER` y `DB_PASS`. Los
procesos batch hijos reciben su propia conexión mediante `BATCH_DB_URL`,
`BATCH_DB_USER` y `BATCH_DB_PASS`, evitando mezclar la tabla de cola con las
tablas de negocio como `results`. Para instalaciones con una base por flujo se
pueden usar `RESULTS_DB_URL`, `FIXTURES_DB_URL`, `TEAM_STATS_DB_URL`,
`MATCH_STATS_DB_URL`, `PLAYER_STATS_DB_URL` y `POINT_BY_POINT_DB_URL`.
