"""Summarize measured samples; uses only the Python standard library."""
import json
import statistics
from collections import defaultdict
from pathlib import Path

root = Path(__file__).resolve().parent
results = root / "results"
samples = [json.loads(line) for line in (results / "samples.jsonl").read_text(encoding="utf-8-sig").splitlines() if line.strip()]
groups = defaultdict(list)
for sample in samples:
    groups[sample["mode"], sample["phase"], sample["rows"]].append(sample)

names = {"direct": "Batch CSV directo", "batch": "Batch + Kafka", "quarkus": "Parser Spring + Kafka + Quarkus", "spring": "Parser Spring + Kafka + persistence Spring"}
summary = []
for (mode, phase, rows), values in groups.items():
    assert len(values) == 3, (mode, phase, rows, len(values))
    assert len({s["fingerprint"] for s in values}) == 1
    result = {"mode": mode, "phase": phase, "rows": rows, "n": len(values)}
    for field in ("elapsedMs", "jobMs", "importStepMs", "workersStartupMs", "startupPlusLoadMs"):
        nums = [v[field] for v in values if field in v]
        if nums:
            result[field] = {"median": statistics.median(nums), "min": min(nums), "max": max(nums)}
    summary.append(result)
assert len(samples) == 45, f"Expected 45 measured samples, got {len(samples)}"
for rows in (75, 7500, 75000):
    assert len({s["fingerprint"] for s in samples if s["rows"] == rows}) == 1, "Different persisted content"
(results / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

def get(mode, phase, rows, field="elapsedMs"):
    return next(s[field] for s in summary if (s["mode"], s["phase"], s["rows"]) == (mode, phase, rows))

def seconds(value):
    return f"{value / 1000:.3f} s"

lines = ["# Comparativa TEAM_STATS — 14 de octubre de 2026", "",
    "45 mediciones válidas, tres repeticiones por combinación. Todos los recuentos y hashes de contenido coinciden entre variantes.", "",
    "## Arrancar aplicaciones y cargar 75 filas", "",
    "PostgreSQL, Kafka y Schema Registry ya disponibles. Procesos Java nuevos. Medianas; Kafka incluye la espera hasta asignar particiones y recibir todas las confirmaciones.", "",
    "| Variante | Arranque + primera carga | Mínimo–máximo |", "| --- | ---: | ---: |"]
for mode in ("direct", "batch", "quarkus", "spring"):
    result = get(mode, "fresh-process" if mode == "direct" else "fresh-workers", 75,
                 "elapsedMs" if mode == "direct" else "startupPlusLoadMs")
    lines.append(f"| {names[mode]} | {seconds(result['median'])} | {seconds(result['min'])}–{seconds(result['max'])} |")

lines += ["", "## Cargas con consumidores Kafka ya activos", "",
    "Mediana desde enviar file.ready hasta recibir confirmaciones por todas las filas. No incluye arranque. Tabla vacía en cada carga.", "",
    "| Filas | Batch + Kafka | Parser + Kafka + Quarkus | Parser + Kafka + Spring |", "| ---: | ---: | ---: | ---: |"]
for rows in (75, 7500, 75000):
    values = [seconds(get(mode, "warm-workers", rows)["median"]) for mode in ("batch", "quarkus", "spring")]
    lines.append(f"| {rows:,} | " + " | ".join(values) + " |")

lines += ["", "## CSV directo, proceso nuevo en cada carga", "",
    "| Filas | Total del proceso | Job interno | Paso leer/persistir |", "| ---: | ---: | ---: | ---: |"]
for rows in (75, 7500, 75000):
    values = [seconds(get("direct", "fresh-process", rows, field)["median"]) for field in ("elapsedMs", "jobMs", "importStepMs")]
    lines.append(f"| {rows:,} | " + " | ".join(values) + " |")

lines += ["", "## Dispersión de cargas calientes", "",
    "| Variante | Filas | Mediana | Mínimo | Máximo |", "| --- | ---: | ---: | ---: | ---: |"]
for mode in ("batch", "quarkus", "spring"):
    for rows in (75, 7500, 75000):
        s = get(mode, "warm-workers", rows)
        lines.append(f"| {names[mode]} | {rows:,} | {seconds(s['median'])} | {seconds(s['min'])} | {seconds(s['max'])} |")

infra = json.loads((results / "infrastructure.json").read_text(encoding="utf-8-sig"))
lines += ["", "## Infraestructura y alcance", "",
    f"- Crear y arrancar PostgreSQL + Kafka + Schema Registry: **{infra['InfrastructureStartupSeconds']:.2f} s** (una medición, imágenes ya descargadas). Este coste está fuera de las tablas.",
    "- Java 21 JVM para todas las aplicaciones; Quarkus no usa una imagen nativa.",
    "- 64 MiB de heap inicial y 512 MiB máximo por proceso; pools de hasta 4 conexiones; bloques/polls de 500.",
    "- Las cargas de 7.500 y 75.000 filas son sintéticas, derivadas del CSV real de 75 filas cambiando los IDs de partido.",
    "- El pipeline Quarkus conserva el parser Spring existente. Las mediciones comparan recorridos completos, no frameworks aislados.",
    "- Las tablas de negocio se vacían entre muestras. No se cronometra la preparación, TRUNCATE, comprobación de contenido ni construcción de artefactos.",
    "- El batch Kafka publica confirmaciones tras importar el fichero; los pipelines publican por poll. El cronómetro espera el total de confirmaciones en todos los casos.",
    "- CSV directo mide hasta salir del proceso y registra resultados localmente; no incluye transporte Kafka de confirmaciones.",
    "- No es una prueba de carga concurrente, ni de disponibilidad, consumo de memoria, actualización de filas existentes o durabilidad ante caídas. Tres repeticiones en una máquina de desarrollo no garantizan el mismo rendimiento en producción.",
    "- Se descartó la primera serie Kafka porque reutilizar grupos tras detener procesos en Windows introducía expiración de consumidores anteriores. Está en initial-series.jsonl y no entra en estas estadísticas. La serie válida usa grupos y topics nuevos por repetición.",
    "", "## Evidencia y reproducción", "",
    "- [Muestras individuales](results/samples.jsonl).", "- [Resumen numérico](results/summary.json).",
    "- [Método y comandos](README.md).", "- [Harness](Benchmark.java).", ""]
(root / "RESULTS.md").write_text("\n".join(lines), encoding="utf-8")
print("\n".join(lines[:35]))
