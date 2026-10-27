package com.example.csvteamstats.batch;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchemaConfiguration {
    @Bean
    FlywayMigrationStrategy migrateBusinessAndBatchSchemas() {
        return business -> {
            // Identical V1/V2 business migrations: compatible with the existing persistence service.
            business.migrate();
            Flyway.configure().dataSource(business.getConfiguration().getDataSource())
                    .schemas("team_stats_batch").defaultSchema("team_stats_batch")
                    .locations("classpath:db/batch").load().migrate();
        };
    }
}
