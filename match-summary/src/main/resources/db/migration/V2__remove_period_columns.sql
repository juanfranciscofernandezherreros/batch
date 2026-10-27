-- Quarter scores belong exclusively to results.results.
ALTER TABLE match_summary.match_summary
    DROP COLUMN IF EXISTS total_local,
    DROP COLUMN IF EXISTS first_local,
    DROP COLUMN IF EXISTS second_local,
    DROP COLUMN IF EXISTS third_local,
    DROP COLUMN IF EXISTS fourth_local,
    DROP COLUMN IF EXISTS extra_local,
    DROP COLUMN IF EXISTS total_away,
    DROP COLUMN IF EXISTS first_away,
    DROP COLUMN IF EXISTS second_away,
    DROP COLUMN IF EXISTS third_away,
    DROP COLUMN IF EXISTS fourth_away,
    DROP COLUMN IF EXISTS extra_away;
