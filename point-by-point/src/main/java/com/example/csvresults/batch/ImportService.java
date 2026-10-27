package com.example.csvpointbypointbatch.batch;

import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ImportService {
    private final CsvInput input; private final JobLauncher launcher; private final JobRepository repository; private final Job job; private final JdbcTemplate jdbc;
    public ImportService(CsvInput input, JobLauncher launcher, JobRepository repository, Job job, JdbcTemplate jdbc) { this.input = input; this.launcher = launcher; this.repository = repository; this.job = job; this.jdbc = jdbc; }
    public JobExecution run(String file, String sourceEventId) throws Exception {
        var path = input.validate(file); var hash = CsvInput.checksum(path); var event = sourceEventId == null ? "csv-sha256-" + hash : sourceEventId;
        var parameters = new JobParametersBuilder().addString("sourceEventId", event).addString("file", path.toString(), false).addString("checksum", hash, false).toJobParameters();
        var previous = repository.getLastJobExecution(job.getName(), parameters);
        if (previous != null && previous.getStatus() == BatchStatus.COMPLETED) { LoggerFactory.getLogger(getClass()).info("event=point_by_point_import_already_completed sourceEventId={}", event); return previous; }
        jdbc.update("INSERT INTO point_by_point_import(event_id,file_path,status,rows_read,rows_persisted,started_at,updated_at) VALUES(?,?, 'RUNNING',0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(event_id) DO UPDATE SET status='RUNNING',updated_at=CURRENT_TIMESTAMP", event, path.toString());
        var execution = launcher.run(job, parameters);
        jdbc.update("UPDATE point_by_point_import SET status=?,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN ?='COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END WHERE event_id=?", execution.getStatus().name(), execution.getStatus().name(), event);
        if (execution.getStatus() != BatchStatus.COMPLETED) throw new ImportFailedException("POINT_BY_POINT job " + execution.getId() + " ended " + execution.getStatus(), execution.getAllFailureExceptions().isEmpty() ? null : execution.getAllFailureExceptions().getFirst());
        LoggerFactory.getLogger(getClass()).info("event=point_by_point_import_completed sourceEventId={} executionId={} read={} written={}", event, execution.getId(), execution.getStepExecutions().iterator().next().getReadCount(), execution.getStepExecutions().iterator().next().getWriteCount());
        return execution;
    }
    public static class ImportFailedException extends RuntimeException { public ImportFailedException(String message, Throwable cause) { super(message, cause); } }
}
