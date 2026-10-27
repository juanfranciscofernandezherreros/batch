package com.example.csvresults.batch;

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
    private final CsvInput input; private final JobLauncher launcher; private final JobRepository repository; private final Job job;
    public ImportService(CsvInput input, JobLauncher launcher, JobRepository repository, Job job) { this.input = input; this.launcher = launcher; this.repository = repository; this.job = job; }
    public JobExecution run(String file, String sourceEventId) throws Exception {
        var path = input.validate(file); var hash = CsvInput.checksum(path); var event = sourceEventId == null ? "csv-sha256-" + hash : sourceEventId;
        var parameters = new JobParametersBuilder().addString("sourceEventId", event).addString("file", path.toString(), false).addString("checksum", hash, false).toJobParameters();
        var previous = repository.getLastJobExecution(job.getName(), parameters);
        boolean hasCompletedPreviousExecution = previous != null && previous.getStatus() == BatchStatus.COMPLETED;
        if (hasCompletedPreviousExecution) { LoggerFactory.getLogger(getClass()).info("event=results_import_already_completed sourceEventId={}", event); return previous; }
        var execution = launcher.run(job, parameters);
        boolean importFailed = execution.getStatus() != BatchStatus.COMPLETED;
        if (importFailed) throw new ImportFailedException("RESULTS job " + execution.getId() + " ended " + execution.getStatus(), execution.getAllFailureExceptions().isEmpty() ? null : execution.getAllFailureExceptions().getFirst());
        LoggerFactory.getLogger(getClass()).info("event=results_import_completed sourceEventId={} executionId={} read={} written={}", event, execution.getId(), execution.getStepExecutions().iterator().next().getReadCount(), execution.getStepExecutions().iterator().next().getWriteCount());
        return execution;
    }
    public static class ImportFailedException extends RuntimeException { public ImportFailedException(String message, Throwable cause) { super(message, cause); } }
}
