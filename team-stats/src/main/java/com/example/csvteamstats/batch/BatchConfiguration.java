package com.example.csvteamstats.batch;

import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfiguration {
    @Bean @StepScope
    TeamStatsCsvReader csvReader(CsvInput input, @Value("#{jobParameters['file']}") String file,
            @Value("#{jobParameters['checksum']}") String checksum) {
        return new TeamStatsCsvReader(input, file, checksum);
    }

    @Bean @StepScope
    TeamStatsWriter statsWriter(JdbcTemplate jdbc,
            @Value("#{stepExecution.jobExecution.jobInstance.instanceId}") Long instance,
            @Value("#{jobParameters['sourceEventId']}") String event,
            @Value("#{jobParameters['sourceName']}") String sourceName,
            @Value("#{jobParameters['sourcePartition']}") Long sourcePartition,
            @Value("#{jobParameters['sourceOffset']}") Long sourceOffset) {
        return new TeamStatsWriter(jdbc, instance, event, sourceName, sourcePartition.intValue(), sourceOffset);
    }

    @Bean
    Step importTeamStats(JobRepository repository, PlatformTransactionManager transactions,
            TeamStatsCsvReader reader, TeamStatsWriter writer, @Value("${app.batch.chunk-size}") int chunkSize) {
        boolean hasPositiveChunkSize = chunkSize >= 1;
        if (!hasPositiveChunkSize) throw new IllegalArgumentException("Chunk size must be positive");
        return new StepBuilder("importTeamStats", repository).<TeamStatsRow, TeamStatsRow>chunk(chunkSize, transactions)
                .reader(reader).writer(writer).build();
    }

    @Bean
    Step publishOutcomes(JobRepository repository, PlatformTransactionManager transactions,
            JdbcTemplate jdbc, OutcomePublisher publisher) {
        return new StepBuilder("publishOutcomes", repository).tasklet((contribution, context) -> {
            long instance = contribution.getStepExecution().getJobExecution().getJobInstance().getInstanceId();
            var events = jdbc.query("""
                    SELECT * FROM team_stats_batch.outcome_events
                    WHERE job_instance_id = ? AND NOT sent ORDER BY id LIMIT 100
                    """, OutcomeEvent::map, instance);
            for (var event : events) {
                publisher.publish(event);
                jdbc.update("UPDATE team_stats_batch.outcome_events SET sent = TRUE WHERE id = ?", event.id());
            }
            return events.isEmpty() ? RepeatStatus.FINISHED : RepeatStatus.CONTINUABLE;
        }, transactions).build();
    }

    @Bean
    Job teamStatsJob(JobRepository repository, Step importTeamStats, Step publishOutcomes) {
        return new JobBuilder("teamStatsImport", repository).start(importTeamStats).next(publishOutcomes).build();
    }

    @Bean
    OutcomePublisher localOutcomes() {
        return event -> LoggerFactory.getLogger("TeamStatsOutcome").info(
                "event=team_stats_outcome type={} sourceEventId={} persistedRecords={} reason={}",
                event.type(), event.sourceEventId(), event.persistedRecords(), event.reason());
    }
}
