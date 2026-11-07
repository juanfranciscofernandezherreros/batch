package com.example.pronostico.standalone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.csvbatchlauncher.pronostico")
public class PronosticoStandaloneApplication {
    public static void main(String[] args) {
        SpringApplication.run(PronosticoStandaloneApplication.class, args);
    }
}
