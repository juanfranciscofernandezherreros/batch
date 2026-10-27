package com.example.csvpointbypointbatch.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResultsBatchApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(ResultsBatchApplication.class, args);
        System.exit(SpringApplication.exit(context));
    }
}
