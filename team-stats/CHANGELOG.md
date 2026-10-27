# Changelog

## 0.1.0

- Aplicación local independiente Spring Batch / Java 21 que combina el parser
  TEAM_STATS y el upsert de persistence.
- Entrada CSV puntual o procesamiento de un directorio compartido.
- Checkpoints PostgreSQL, protección por checksum, deduplicación por clave natural,
  resultados transaccionales y reintento de publicación.
- Docker Compose para ejecutar el batch como proceso puntual.
- Pruebas unitarias y de integración de PostgreSQL y reanudación.
