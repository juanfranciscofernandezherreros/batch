# csv-results-batch

Batch autónomo para leer ficheros `RESULTS_*.csv` y hacer upsert en PostgreSQL. No contiene Kafka ni necesita que haya ningún proceso 24/7 levantado.

El nombre esperado es `RESULTS_<timestamp>_<country>_<competition>.csv` y el CSV debe tener las 16 columnas del contrato de `csv-results-parser`: `matchId`, `eventTime`, equipos, marcador total y los cinco parciales de cada equipo.

## Ejecución

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
mvn -B spring-boot:run "-Dspring-boot.run.arguments=--file=C:\data\csv\RESULTS_20261014_ES_ACB.csv"
```

Para procesar los CSV pendientes de un directorio:

```powershell
mvn -B spring-boot:run "-Dspring-boot.run.arguments=--directory=C:\data\csv"
```

La ruta debe estar bajo `CSV_ALLOWED_ROOT`. La conexión se configura con `DB_URL`, `DB_USER` y `DB_PASS`; el tamaño del chunk con `BATCH_CHUNK_SIZE`.

Spring Batch guarda el estado, checkpoints y el resultado de cada ejecución en el esquema `results_batch`. Una ejecución completada con el mismo checksum se considera idempotente. Si el watcher detecta un fichero nuevo, puede lanzar este proceso con `--file=<ruta>`; el watcher sigue siendo el responsable de detectar y mover el fichero, y el batch de leerlo, persistirlo y terminar.

```text
csv-watcher -> file.ready -> csv-file-event-router -> csv-results-batch -> PostgreSQL
```

No hay topic de éxito: el estado `COMPLETED`/`FAILED`, los contadores de la ejecución y los logs de `ImportService` son la confirmación del procesamiento.

## Verificación

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
mvn -B test
```
