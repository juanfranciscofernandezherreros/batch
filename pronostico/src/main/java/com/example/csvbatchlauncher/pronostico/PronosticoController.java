package com.example.csvbatchlauncher.pronostico;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/predictions/euroleague")
public class PronosticoController {
    private final PronosticoService service;

    public PronosticoController(PronosticoService service) {
        this.service = service;
    }

    @PostMapping("/next")
    public ResponseEntity<PublishedPrediction> publishNextRound() {
        return ResponseEntity.ok(service.publishNextRound());
    }
}
