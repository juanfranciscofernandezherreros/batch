-- Flashscore can expose the same match through translated competition slugs.
WITH ranked AS (
    SELECT ctid,
           row_number() OVER (
               PARTITION BY match_id
               ORDER BY (event_datetime IS NOT NULL) DESC,
                        CASE WHEN country = 'europe' THEN 0 ELSE 1 END,
                        CASE WHEN competition = 'euroleague' THEN 0 ELSE 1 END,
                        ctid
           ) AS position
    FROM fixtures
)
DELETE FROM fixtures f
USING ranked r
WHERE f.ctid = r.ctid
  AND r.position > 1;

ALTER TABLE fixtures DROP CONSTRAINT IF EXISTS fixtures_pkey;
ALTER TABLE fixtures ADD CONSTRAINT fixtures_pkey PRIMARY KEY (match_id);
