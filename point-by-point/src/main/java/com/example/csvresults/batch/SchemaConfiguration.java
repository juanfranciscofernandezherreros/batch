package com.example.csvpointbypointbatch.batch;

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
                    .schemas("point_by_point_batch").defaultSchema("point_by_point_batch")
                    .locations("classpath:db/batch")
                    .load().migrate();
        };
    }
}
