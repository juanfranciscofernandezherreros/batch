package com.example.csvextra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExtraBatchApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(ExtraBatchApplication.class, args);
        System.exit(SpringApplication.exit(context));
    }
}
