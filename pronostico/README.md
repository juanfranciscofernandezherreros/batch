# Módulo `pronostico`

El launcher publica la siguiente jornada de EuroLeague al iniciar. También se puede
reintentar de forma explícita con `POST http://localhost:8088/predictions/euroleague/next`.
El módulo se puede levantar por separado en el puerto 8091 y publica al arrancar; su
endpoint manual es `POST http://localhost:8091/predictions/euroleague/next`. La operación
es idempotente: actualiza el artículo de esa jornada en `prediction_articles`.

## Dependencia de la librería de artículos

La librería indicada aún no está publicada en Maven Central; su propio README pide
instalarla primero en el repositorio Maven local. En una máquina nueva:

```powershell
git clone https://github.com/juanfranciscofernandezherreros/ai-generic-generation-content.git C:\Develop\basketball\ai-generic-generation-content
mvn -f C:\Develop\basketball\ai-generic-generation-content\ai-generic-generation-content\pom.xml -DskipTests install
mvn -f C:\Develop\basketball\csv-extra-batch\pom.xml -pl launcher -am -DskipTests package
mvn -f C:\Develop\basketball\csv-extra-batch\pronostico\pom.xml spring-boot:run
```

Se configura Ollama con estas variables opcionales:

```text
PREDICTION_OLLAMA_BASE_URL=http://localhost:11434
PREDICTION_OLLAMA_MODEL=llama3.1
PRONOSTICO_RUN_ON_STARTUP=true
```

El launcher usa PostgreSQL `localhost:55432/basketball` y MongoDB
`mongodb://localhost:27017/basketball` por defecto. Para una prueba manual, puede
desactivarse la generación automática con `PRONOSTICO_RUN_ON_STARTUP=false` y llamar al
endpoint `POST`.

## Criterio y límites

El selector numérico usa únicamente resultados anteriores a la jornada: 65% diferencial
medio de temporada, 35% diferencial medio de los últimos cinco partidos y 2,5 puntos de
localía; convierte el margen esperado a probabilidad con una logística de escala 9. Ollama
redacta el artículo a partir del mismo contexto, pero no decide ni altera las selecciones.

El artículo incorpora estadísticas colectivas no defectuosas, líderes de anotación que
tengan al menos tres apariciones, disponibilidad de resúmenes y secuencias, y balance y
diferencial de puntos. Excluye porcentajes de tiro de `team_stats` porque sus columnas
están inconsistentes; la base no contiene cuotas ni estado de lesiones. Las probabilidades
son heurísticas, no están calibradas con temporadas históricas.
