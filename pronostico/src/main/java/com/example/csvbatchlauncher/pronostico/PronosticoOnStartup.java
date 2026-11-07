package com.example.csvbatchlauncher.pronostico;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PronosticoOnStartup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PronosticoOnStartup.class);
    private final PronosticoService service;
    private final boolean enabled;

    public PronosticoOnStartup(PronosticoService service,
            @Value("${app.pronostico.run-on-startup:${PRONOSTICO_RUN_ON_STARTUP:true}}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        CompletableFuture.runAsync(() -> {
            try {
                PublishedPrediction published = service.publishNextRound();
                log.info("event=euroleague_prediction_published id={} round={} games={} url={}",
                        published.id(), published.round(), published.games(), published.url());
            } catch (RuntimeException exception) {
                log.error("event=euroleague_prediction_startup_failed message={}", exception.getMessage(), exception);
            }
        });
    }
}
