# csv-fixtures-batch

Batch autónomo para leer ficheros `FIXTURES_*.csv` y hacer upsert en PostgreSQL. No contiene Kafka y se levanta solo durante la importación.

Acepta el formato actual `FIXTURES_<timestamp>_<country>_<competition>.csv` y el formato legacy `FIXTURES_<country>_<competition>.csv`. La cabecera debe ser `match_id,event_time,home_team,away_team`.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
mvn -B spring-boot:run "-Dspring-boot.run.arguments=--file=C:\data\csv\FIXTURES_20261014120000_ES_ACB.csv"
```

Para un directorio completo:

```powershell
mvn -B spring-boot:run "-Dspring-boot.run.arguments=--directory=C:\data\csv"
```

La clave de negocio es `(match_id, country, competition)`. Los estados y checkpoints quedan en `fixtures_batch`; no hay topic de éxito. El watcher puede lanzar el proceso con `--file` cuando detecte un fichero nuevo:

```text
csv-watcher -> file.ready -> csv-file-event-router -> csv-fixtures-batch -> PostgreSQL
```

Configuración: `DB_URL`, `DB_USER`, `DB_PASS`, `CSV_ALLOWED_ROOT`, `BATCH_CHUNK_SIZE`, `FIXTURES_MAX_FILE_SIZE_BYTES` y `FIXTURES_MAX_ROWS_PER_FILE`.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
mvn -B test
```
