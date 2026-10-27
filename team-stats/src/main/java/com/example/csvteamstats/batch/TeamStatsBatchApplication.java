package com.example.csvteamstats.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TeamStatsBatchApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(TeamStatsBatchApplication.class, args);
        System.exit(SpringApplication.exit(context));
    }
}
