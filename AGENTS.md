# Repository Guidelines

## Project Structure & Module Organization

This Maven reactor ingests basketball CSV data into PostgreSQL. The root `pom.xml` builds all modules: shared contracts live in `csv-batch-core`; CSV-specific batch applications live in `results`, `fixtures`, `match-summary`, `team-stats`, `stats-player`, `point-by-point`, and `extra-importer`; `launcher` exposes the batch-launching service.

Within a module, keep application code in `src/main/java`, configuration and Flyway migrations in `src/main/resources` (for example, `db/migration/V1__create_results.sql`), and tests in `src/test/java`. Generated JARs and compiler output belong in `target/` and must not be edited. Start with `START_HERE.md`; CSV contracts and load order are documented in `README.md` and `DOCUMENTATION.md`.

## Build, Test, and Development Commands

Use Java 21 and Maven from the repository root:

```powershell
mvn clean verify                 # compile all modules and run unit/integration tests
mvn -pl team-stats test          # run one module's unit tests
mvn -pl team-stats verify        # include that module's integration tests
mvn -pl launcher spring-boot:run # run the launcher locally
```

Set `DB_URL`, `DB_USER`, `DB_PASS`, `DB_SCHEMA`, and `CSV_ALLOWED_ROOT` before executing an importer. Local defaults and expected paths are described in `README.md`.

## Coding Style & Naming Conventions

Write Java with four-space indentation and standard Spring conventions: `PascalCase` classes, `camelCase` fields/methods, and packages under `com.example`. Keep CSV row models and readers close to their owning batch module. Name Flyway migrations sequentially as `V<version>__<lowercase_description>.sql`; never alter an applied migration—add a new one instead. No formatter or linter is configured, so preserve the surrounding file's style and keep changes focused.

For Java changes, read and follow `.codex/instructions/if-naming.md`.

## Testing Guidelines

Tests use Spring Boot Test/JUnit Jupiter. Name unit tests `*Test` and integration tests `*IT`; the latter run during `verify` through Failsafe. Add coverage for CSV header validation, parsing edge cases, idempotent database writes, and migrations whenever behavior changes. `team-stats` integration tests use Testcontainers PostgreSQL and require Docker.

## Commit & Pull Request Guidelines

This checkout has no Git history to establish a local convention. Use concise, imperative commits scoped to a module, e.g. `results: validate event datetime`. Keep unrelated changes separate. Pull requests should explain the affected CSV contract/database schema, list validation commands run, link the issue when available, and include sample input/output or screenshots for launcher API changes.
