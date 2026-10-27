package com.example.csvteamstats.batch;

import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

@Service
public class ImportService {
    private final CsvInput input;
    private final JobLauncher launcher;
    private final JobRepository repository;
    private final Job job;

    public ImportService(CsvInput input, JobLauncher launcher, JobRepository repository, Job job) {
        this.input = input; this.launcher = launcher; this.repository = repository; this.job = job;
    }

    public JobExecution run(String file, String sourceEventId, String sourceName, int sourcePartition, long sourceOffset) throws Exception {
        var path = input.validate(file);
        var hash = CsvInput.checksum(path);
        var event = sourceEventId == null ? "csv-sha256-" + hash : sourceEventId;
        if (event.isBlank() || event.length() > 255) throw new IllegalArgumentException("sourceEventId must contain 1..255 characters");
        // Stable identity: retries do not create a fresh job and duplicate committed chunks.
        var parameters = new JobParametersBuilder().addString("sourceEventId", event)
                .addString("file", path.toString(), false).addString("checksum", hash, false)
                .addString("sourceName", sourceName, false).addLong("sourcePartition", (long) sourcePartition, false)
                .addLong("sourceOffset", sourceOffset, false).toJobParameters();
        var previous = repository.getLastJobExecution(job.getName(), parameters);
        if (previous != null) {
            if (!hash.equals(previous.getJobParameters().getString("checksum"))) {
                throw new IllegalArgumentException("sourceEventId already belongs to different CSV contents; use a new sourceEventId");
            }
            if (previous.getStatus() == BatchStatus.COMPLETED) {
                LoggerFactory.getLogger(getClass()).info("event=team_stats_import_already_completed sourceEventId={}", event);
                return previous;
            }
        }
        var execution = launcher.run(job, parameters);
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            var causes = execution.getAllFailureExceptions();
            throw new ImportFailedException("TEAM_STATS job " + execution.getId() + " ended " + execution.getStatus(),
                    causes.isEmpty() ? null : causes.getFirst());
        }
        LoggerFactory.getLogger(getClass()).info("event=team_stats_import_completed sourceEventId={} executionId={}", event, execution.getId());
        return execution;
    }

    public static class ImportFailedException extends RuntimeException {
        public ImportFailedException(String message, Throwable cause) { super(message, cause); }
    }
}
