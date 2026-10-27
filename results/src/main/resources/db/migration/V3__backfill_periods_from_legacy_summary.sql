-- V3: one-time preservation of legacy quarter scores before match_summary drops them.
-- The old SCORE_SUMMARY writer stored period 1 in total_local/total_away,
-- then periods 2-5 in first..fourth.  RESULTS is now the sole owner.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'match_summary' AND table_name = 'match_summary'
      AND column_name = 'total_local'
  ) THEN
    UPDATE results.results r
    SET home_score1 = COALESCE(r.home_score1, NULLIF(s.total_local, '')::INTEGER),
        home_score2 = COALESCE(r.home_score2, NULLIF(s.first_local, '')::INTEGER),
        home_score3 = COALESCE(r.home_score3, NULLIF(s.second_local, '')::INTEGER),
        home_score4 = COALESCE(r.home_score4, NULLIF(s.third_local, '')::INTEGER),
        home_score5 = COALESCE(r.home_score5, NULLIF(s.fourth_local, '')::INTEGER),
        away_score1 = COALESCE(r.away_score1, NULLIF(s.total_away, '')::INTEGER),
        away_score2 = COALESCE(r.away_score2, NULLIF(s.first_away, '')::INTEGER),
        away_score3 = COALESCE(r.away_score3, NULLIF(s.second_away, '')::INTEGER),
        away_score4 = COALESCE(r.away_score4, NULLIF(s.third_away, '')::INTEGER),
        away_score5 = COALESCE(r.away_score5, NULLIF(s.fourth_away, '')::INTEGER)
    FROM match_summary.match_summary s
    WHERE r.match_id = s.match_id
      AND (r.home_score1 IS NULL OR r.away_score1 IS NULL)
      AND s.total_local ~ '^[0-9]+$' AND s.first_local ~ '^[0-9]+$'
      AND s.second_local ~ '^[0-9]+$' AND s.third_local ~ '^[0-9]+$'
      AND s.total_away ~ '^[0-9]+$' AND s.first_away ~ '^[0-9]+$'
      AND s.second_away ~ '^[0-9]+$' AND s.third_away ~ '^[0-9]+$'
      AND (NULLIF(s.fourth_local, '') IS NULL OR s.fourth_local ~ '^[0-9]+$')
      AND (NULLIF(s.fourth_away, '') IS NULL OR s.fourth_away ~ '^[0-9]+$');
  END IF;
END $$;
