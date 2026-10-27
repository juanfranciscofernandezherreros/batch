package com.example.csvstatsmatchbatch.batch;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchemaConfiguration {
    @Bean
    FlywayMigrationStrategy migrateBusinessAndBatchSchemas() {
        return business -> {
            business.migrate();
            Flyway.configure().dataSource(business.getConfiguration().getDataSource())
                    .schemas("stats_match_batch").defaultSchema("stats_match_batch")
                    .locations("classpath:db/batch")
                    .load().migrate();
        };
    }
}
