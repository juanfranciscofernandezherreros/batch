package com.example.csvbatchlauncher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(LauncherProperties.class)
@EnableScheduling
public class CsvBatchLauncherApplication {
    public static void main(String[] args) { SpringApplication.run(CsvBatchLauncherApplication.class, args); }
}
