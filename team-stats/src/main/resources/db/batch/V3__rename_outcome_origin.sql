DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'team_stats_batch' AND table_name = 'outcome_events' AND column_name = 'original_topic') THEN
        ALTER TABLE outcome_events RENAME COLUMN original_topic TO input_source;
        ALTER TABLE outcome_events RENAME COLUMN original_partition TO input_partition;
        ALTER TABLE outcome_events RENAME COLUMN original_offset TO input_offset;
    END IF;
END $$;
