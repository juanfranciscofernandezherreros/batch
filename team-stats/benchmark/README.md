# Comparativa local TEAM_STATS

Harness opt-in de medición de los artefactos compilados existentes. No cambia el código
de los servicios comparados. Requiere Java 21, Maven, Docker y estos artefactos:

- `../target/app.jar` (batch).
- `../../csv-team-stats-parser/target/app.jar`.
- `../../csv-team-stats-persistence/target/app.jar`.
- `../../csv-team-stats-persistence-quarkus/target/quarkus-app/quarkus-run.jar` y sus bibliotecas.

## Método

- PostgreSQL 16 y Kafka/Schema Registry 7.5 en un proyecto Compose aislado.
- Puertos locales 55440, 19092, 18081. Cuatro bases `bench_direct`, `bench_batch`,
  `bench_spring`, `bench_quarkus`; topics y grupos exclusivos de la ejecución.
- Mismo Java 21, `-Xms64m -Xmx512m` por proceso; máximo 4 conexiones por pool;
  bloques/polls de hasta 500 registros, un consumidor por etapa.
- 75 filas reales y 7.500/75.000 filas sintéticas generadas repitiendo sus valores
  con identificadores de partido distintos. No representa datos nuevos de partidos reales.
- Tres repeticiones por combinación. La tabla de negocio se vacía antes de cada
  medición; TRUNCATE, preparación del CSV y verificación quedan fuera del cronómetro.
- CSV directo: proceso nuevo en cada repetición, desde el lanzamiento hasta su salida.
  También se extraen del log las duraciones internas de paso y job.
- Kafka: se lanzan los consumidores y se espera a su asignación de particiones;
  se miden arranque, primera carga y cargas con los procesos ya activos.
  Cada repetición de arranque usa topics y grupos nuevos para evitar que la espera
  de expiración de consumidores anteriores en Windows se confunda con el arranque.
- Antes de las cargas calientes se procesa un CSV de 75.000 filas sin incluirlo en
  las estadísticas. Mantiene conexiones, JIT y cachés del consumidor calientes.
- Latencia Kafka: desde el envío del evento file.ready hasta recibir todas las
  confirmaciones de persistencia. Incluye el productor de prueba y la lectura de las
  confirmaciones. Se comprueban después commits de offsets, filas, source_event_id
  y hash de todas las columnas de negocio, excluyendo IDs técnicos y timestamps.
- Los tiempos Kafka incluyen las notificaciones; el CSV directo registra resultados
  localmente. Es una comparación de recorridos de aplicación, no un microbenchmark
  que aísle los frameworks. Quarkus utiliza JVM, no compilación nativa.
- El primer arranque de cada consumidor puede incluir las migraciones de su base;
  las siguientes repeticiones reutilizan el esquema. CSV directo tiene una ejecución
  inicial de preparación fuera de las muestras.
- El arranque de infraestructura se mide por separado. Las imágenes ya están
  descargadas. Los tiempos de las aplicaciones no incluyen arranque de Docker Desktop,
  descarga de imágenes, compilación ni preparación de los ficheros.

## Repetir

Desde el directorio del proyecto, con Java 21 en PATH:

```powershell
docker compose -f benchmark/compose.yaml up -d --wait --wait-timeout 180
mvn -B -ntp dependency:build-classpath '-Dmdep.outputFile=target/benchmark-classpath.txt' '-DincludeScope=test'
$benchmarkClasspath = (Get-Content -Raw target/benchmark-classpath.txt).Trim()
javac -cp $benchmarkClasspath -d target/benchmark-harness benchmark/Benchmark.java
java -cp "target/benchmark-harness;$benchmarkClasspath" Benchmark 'C:/ruta/al/CSV/real/de/75/filas.csv'
docker compose -f benchmark/compose.yaml down --volumes
```

El stack debe ser nuevo: el harness crea sus bases y se detiene si ya existen.
Las muestras se añaden a `results/samples.jsonl`; archiva los resultados anteriores
antes de una nueva ejecución para no mezclar series.
Si falla alguna carga, consulta los logs de `results/`. El harness termina sus
procesos hijos incluso al fallar. Ejecuta siempre el último comando para retirar
exclusivamente los contenedores de esta comparativa.
