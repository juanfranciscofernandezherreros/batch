package com.example.csvstatsplayerbatch.batch;

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
                    .schemas("stats_player_batch").defaultSchema("stats_player_batch")
                    .locations("classpath:db/batch")
                    .load().migrate();
        };
    }
}
