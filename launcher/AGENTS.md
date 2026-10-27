# csv-batch-launcher instructions

## Mandatory CSV ingestion order

For any full ingestion, submit datasets in this exact order: `RESULTS`,
`FIXTURES`, `SCORE_SUMMARY`, `STATS_ALL_PERIODS`, `PLAYER_STATS`, then
`POINT_BY_POINT`. Do not process a directory in lexical path order. `RESULTS`
must always be submitted first because it establishes the match records needed
by subsequent datasets.

## Canonical database

Use `jdbc:postgresql://localhost:55432/basketball` with user `test` and
password `test` on the host. All flows use schemas in this one database.

## Canonical input folder

The launcher accepts only `C:\data\basketball-ready\current`. Raw league data
must remain in `C:\data\basketball\competitions\<country>\<competition>` and
pass through `consolidate_csv.py`; never scan or submit raw competition folders.
