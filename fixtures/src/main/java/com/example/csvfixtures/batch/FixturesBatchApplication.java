package com.example.csvfixtures.batch;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class FixturesBatchApplication {
    public static void main(String[] args) { var context = SpringApplication.run(FixturesBatchApplication.class, args); System.exit(SpringApplication.exit(context)); }
}
