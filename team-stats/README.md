# csv-team-stats-batch

Aplicación Spring Batch independiente para leer CSV TEAM_STATS y persistirlos en PostgreSQL.
El proyecto **no depende de Kafka, Schema Registry ni de ningún broker**. El watcher puede
dejar los ficheros en un directorio compartido; el batch se ejecuta bajo demanda y termina.

## Flujo

```text
csv-watcher → directorio CSV compartido → csv-team-stats-batch → PostgreSQL
```

`csv-file-event-router`, Kafka, `csv-team-stats-parser` y `csv-team-stats-persistence`
pertenecen al flujo anterior y no se arrancan con esta aplicación.

## Compilar

```powershell
mvn -B test
mvn -B package
```

Requiere Java 21, PostgreSQL y el artefacto Maven `csv-processing-core:1.0.1`.

## Importar un fichero

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:55432/basketball'
$env:DB_USER = 'test'
$env:DB_PASS = 'test'
$env:CSV_ALLOWED_ROOT = 'C:/data/basketball'
java -jar target/app.jar --file=C:/data/basketball/partido/stats_all_periods.csv
```

El proceso valida el fichero, ejecuta el job, persiste por bloques y termina con código
0 si finaliza correctamente. También admite `--source-event-id=<id>` para identificar
una importación explícitamente.

## Importar todo un directorio

```powershell
java -jar target/app.jar --directory=C:/data/basketball/ready/team-stats
```

Procesa los CSV en orden, uno detrás de otro, y termina. Conviene que el watcher escriba
primero a un nombre temporal y renombre a `.csv` cuando el fichero esté completo.

## Cómo reaccionar cuando el watcher detecta un fichero

Sin Kafka tienes dos opciones:

1. El watcher copia el CSV a `ready/team-stats` y el Programador de tareas, cron o un
   job de Kubernetes ejecuta `--directory` cada cierto tiempo.
2. El watcher ejecuta directamente el proceso después de cerrar el fichero:

   ```powershell
   java -jar C:/Develop/basketball/csv-team-stats-batch/target/app.jar `
     --file=C:/data/basketball/ready/team-stats/STATS_ALL_PERIODS_20261014120000_match.csv
   ```

El watcher actual publica eventos Kafka; ese mecanismo no puede activar este batch porque
este proyecto no tiene consumidor Kafka. Para usarlo hay que cambiar esa salida por una
acción local (copiar el fichero o ejecutar el proceso), o poner un launcher externo que
traduzca el evento a una ejecución del comando.

## Estado de las ejecuciones

No se publica un topic de éxito. El estado queda en PostgreSQL:

```sql
SELECT job_execution_id, status, start_time, end_time, exit_code, exit_message
FROM team_stats_batch.batch_job_execution
ORDER BY job_execution_id DESC
LIMIT 10;
```

Los estados son `STARTING`, `STARTED`, `COMPLETED`, `FAILED` o `STOPPED`. Los bloques y
recuentos quedan auditados en `team_stats_batch.outcome_events`. Una ejecución completada
con el mismo `source-event-id` y contenido no vuelve a importar el fichero.

## Persistencia y Docker

- Usa `public.team_stats` y las mismas migraciones que persistence.
- Upsert por `(match_id, period, category, metric)`; los reintentos no duplican filas.
- Checkpoints y escrituras de negocio se confirman por bloque.
- El CSV debe estar bajo `CSV_ALLOWED_ROOT` y ser inmutable durante la ejecución.

```powershell
docker compose -f compose.yaml -f compose.local.yaml run --rm --build `
  batch-csv --directory=/data/csv/ready/team-stats
```
