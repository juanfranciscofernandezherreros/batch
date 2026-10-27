package com.example.csvpointbypointbatch.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfiguration {
    @Bean @StepScope ResultsCsvReader csvReader(CsvInput input, @Value("#{jobParameters['file']}") String file, @Value("#{jobParameters['checksum']}") String checksum) { return new ResultsCsvReader(input, file, checksum); }
    @Bean @StepScope ResultsWriter resultsWriter(JdbcTemplate jdbc, @Value("#{jobParameters['file']}") String file) { return new ResultsWriter(jdbc, java.nio.file.Path.of(file).getFileName().toString()); }
    @Bean Step importResults(JobRepository repo, PlatformTransactionManager tx, ResultsCsvReader reader, ResultsWriter writer, @Value("${app.batch.chunk-size}") int size) {
        boolean hasPositiveChunkSize = size >= 1;
        if (!hasPositiveChunkSize) throw new IllegalArgumentException("Chunk size must be positive");
        return new StepBuilder("importResults", repo).<ResultRow, ResultRow>chunk(size, tx).reader(reader).writer(writer).build();
    }
    @Bean Job resultsJob(JobRepository repo, Step importResults) { return new JobBuilder("resultsImport", repo).start(importResults).build(); }
}
