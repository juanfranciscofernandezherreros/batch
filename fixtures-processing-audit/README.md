# fixtures-processing-audit

Módulo independiente que, al arrancar, audita todos los registros de `fixtures.fixtures`. Un fixture es exigible solo cuando su fecha de calendario en `Europe/Madrid` es anterior a hoy; la hora no se usa para incluir partidos del día actual.

Para cada fixture finalizado cruza `match_id` contra `results.results`, `match_summary.match_summary`, `point_by_point.point_by_point_event`, `team_stats.team_stats` y `stats_player.stats_player`.

Genera dos CSV en `C:\data\basketball\reports\orchestrator`:

- `FIXTURES_PROCESSING_AUDIT_<timestamp>.csv`: todos los fixtures y sus datasets pendientes.
- `FIXTURES_PROCESSING_AUDIT_SUMMARY_<timestamp>.csv`: resumen por país y competición.

El resumen incorpora también todas las ligas de `LIGAS_BASKET.json`; una liga sin filas en `fixtures.fixtures` queda marcada como `SIN_FIXTURES_IMPORTADOS`.

Arranque:

```powershell
cd C:\Develop\basketball\csv-extra-batch
& .\mvnw.cmd -pl fixtures-processing-audit -am spring-boot:run
```

Para ejecutar una auditoría puntual, listar todos los `match_id` pendientes y terminar el proceso:

```powershell
& C:\Develop\basketball\1_EJECUTAR\AUDITORIA_FIXTURES.bat
```

El BAT crea también `FIXTURES_PROCESSING_AUDIT_PENDING_<timestamp>.csv` con la lista filtrada.

Para procesar exclusivamente los partidos de esa lista mediante el pipeline aislado por `match_id`:

```powershell
& C:\Develop\basketball\1_EJECUTAR\MATCHIDS_PENDIENTES.bat
& C:\Develop\basketball\1_EJECUTAR\MATCHIDS_PENDIENTES.bat "C:\data\basketball\reports\orchestrator\FIXTURES_PROCESSING_AUDIT_PENDING_<timestamp>.csv"
& C:\Develop\basketball\1_EJECUTAR\MATCHIDS_PENDIENTES.bat "C:\ruta\pendientes.csv" 10
```

El segundo argumento es opcional: `0` (valor predeterminado) procesa todos los partidos; otro valor positivo limita la ejecución. El proceso inicia el runtime, arranca el `csv-batch-launcher` si no está disponible, registra una fila y log por partido y ejecuta una auditoría final.

Endpoints:

- `GET http://localhost:8090/fixtures-processing-audit/latest`
- `POST http://localhost:8090/fixtures-processing-audit/run`

Configuración opcional: `FIXTURES_PROCESSING_AUDIT_PORT`, `FIXTURES_PROCESSING_AUDIT_TIMEZONE`, `FIXTURES_PROCESSING_AUDIT_REPORT_DIRECTORY`, `FIXTURES_PROCESSING_AUDIT_LEAGUE_CATALOG` y `FIXTURES_PROCESSING_AUDIT_ENABLED`.


Monitor de cola en vivo (servicio en segundo plano y consola visible):

```bat
C:\Develop\basketball\1_EJECUTAR\MONITOR_PENDIENTES.bat
``` 

El servicio se comprueba y, si hace falta, se arranca con `scripts\arranque\ASEGURAR.bat auditoria`; GET http://localhost:8090/fixtures-processing-audit/latest devuelve la cola completa, con refresco cada 5 segundos.
