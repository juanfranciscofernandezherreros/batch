package com.example.fixturesprocessingaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FixturesProcessingAuditApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixturesProcessingAuditApplication.class, args);
    }
}
